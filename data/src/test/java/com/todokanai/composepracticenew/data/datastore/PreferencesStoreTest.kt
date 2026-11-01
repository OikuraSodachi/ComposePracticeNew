package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.todokanai.composepracticenew.model.PreferenceFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** [PreferencesStore]의 읽기 재시도·실패 시 기본값 방출, 읽기·저장 실패 알림, 저장이 호출 순서대로 하나씩 반영되는지를 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesStoreTest {

    private val key = stringPreferencesKey("k")
    private val stored = preferencesOf(key to "v")

    /** 소비자 코루틴 스코프를 만들고 정리하며, 그 안에서 잡히지 않은 예외를 테스트 실패로 드러내는 규칙. */
    @get:Rule
    val storeScopes = StoreScopeRule()

    /** 검증 대상이 실패를 넣는 대기열. */
    private val failures = PreferenceFailureChannel()

    @Test
    fun `읽기 IOException 시 기본값을 한 번 방출하고 재시도 후 실제 값을 방출한다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingReads = 2))
        val emitted = mutableListOf<Preferences>()
        val job = launch { store.data.take(2).toList(emitted) }

        runCurrent()
        assertEquals(listOf(emptyPreferences()), emitted) // 첫 실패에만 기본값

        advanceTimeBy(1_000 + 2_000 + 1) // 1초, 2초 백오프 후 세 번째 수집 성공
        runCurrent()
        assertEquals(listOf(emptyPreferences(), stored), emitted)
        job.join()
    }

    @Test
    fun `읽기 실패는 여러 곳이 수집해도 실패가 이어지는 동안 한 번만 알린다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingReads = 3))
        val reported = collectFailures()
        val first = launch { store.data.take(2).toList() }
        val second = launch { store.data.take(2).toList() }

        advanceUntilIdle()

        assertEquals(listOf(PreferenceFailure.READ), reported)
        first.join()
        second.join()
    }

    @Test
    fun `읽기가 회복된 뒤 다시 실패하면 다시 알린다`() = runTest {
        val fake = FakePreferencesDataStore(stored, failingReads = 1)
        val store = storeOf(fake)
        val reported = collectFailures()

        store.data.take(2).toList() // 실패 → 기본값 → 재시도 성공
        fake.failNextReads(1)
        assertNull(store.readOnce())
        runCurrent()

        assertEquals(listOf(PreferenceFailure.READ, PreferenceFailure.READ), reported)
    }

    @Test
    fun `재시도 성공 후에도 Flow가 유지되어 이후 저장을 반영한다`() = runTest {
        val fake = FakePreferencesDataStore(stored, failingReads = 1)
        val store = storeOf(fake)
        val emitted = mutableListOf<Preferences>()
        val job = launch { store.data.take(3).toList(emitted) }

        advanceTimeBy(1_001)
        runCurrent()
        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(listOf(emptyPreferences(), stored, preferencesOf(key to "w")), emitted)
        job.join()
    }

    @Test
    fun `IOException이 아닌 읽기 예외는 전파된다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingReads = 1, readError = IllegalStateException("x")))
        val result = runCatching { store.data.take(1).toList() }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `한 번 읽기는 지금 값을 돌려준다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored))
        assertEquals(stored, store.readOnce())
    }

    @Test
    fun `한 번 읽기가 실패하면 기본값 대신 null을 돌려주고 읽기 실패를 알린다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingReads = 1))
        val reported = collectFailures()

        assertNull(store.readOnce())
        runCurrent()

        assertEquals(listOf(PreferenceFailure.READ), reported)
    }

    @Test
    fun `저장 IOException이면 기존 값이 유지되고 저장 실패를 알린다`() = runTest {
        val fake = FakePreferencesDataStore(stored, writeError = IOException("write"))
        val store = storeOf(fake)
        val reported = collectFailures()

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle() // 예외가 잡히지 않으면 StoreScopeRule이 테스트를 실패시킨다

        assertEquals(stored, fake.state.value)
        assertEquals(listOf(PreferenceFailure.SAVE), reported)
    }

    @Test
    fun `저장 성공 시 값이 반영되고 실패를 알리지 않는다`() = runTest {
        val fake = FakePreferencesDataStore(stored)
        val store = storeOf(fake)
        val reported = collectFailures()

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "w"), fake.state.value)
        assertEquals(emptyList<PreferenceFailure>(), reported)
    }

    @Test
    fun `앞선 편집이 반영 중일 때 들어온 편집은 그 뒤에 반영된다`() = runTest {
        // 첫 편집은 반영 전 100ms를 기다린다 — 호출마다 따로 launch하던 이전 구현에서는 두 번째 편집이 먼저 반영되고 첫 편집이 덮어써 "first"가 남는다
        val fake = FakePreferencesDataStore(stored, writeDelays = mutableListOf(100L, 0L))
        val store = storeOf(fake)

        store.launchEdit { it[key] = "first" }
        runCurrent() // 소비자가 첫 편집을 꺼내 반영을 시작한 상태
        store.launchEdit { it[key] = "second" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "second"), fake.state.value)
        assertEquals(2, fake.writeCount)
    }

    @Test
    fun `대기열에 함께 쌓인 편집도 하나씩 넣은 순서대로 반영되고, 저장 실패는 그 편집에만 해당한다`() = runTest {
        // 첫 쓰기만 실패한다 — 쌓인 편집을 한 번에 쓰던 이전 구현에서는 세 편집이 함께 사라진다
        val fake = FakePreferencesDataStore(stored, failingWrites = 1)
        val store = storeOf(fake)
        val reported = collectFailures()

        store.launchEdit { it[key] = "lost" }
        store.launchEdit { it[key] = it[key] + "b" }
        store.launchEdit { it[key] = it[key] + "c" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "vbc"), fake.state.value)
        assertEquals(3, fake.writeCount)
        assertEquals(listOf(PreferenceFailure.SAVE), reported)
    }

    /** [fake] 위에 검증 대상을 만든다. */
    private fun TestScope.storeOf(fake: FakePreferencesDataStore) = PreferencesStore(fake, storeScopes.scopeFor(this), failures)

    /**
     * 알린 실패를 테스트가 끝날 때까지 모아 두는 목록을 돌려준다.
     * 수집은 backgroundScope가 아니라 소비자와 같은 스코프에서 한다 — advanceUntilIdle은 background 작업을 끝까지 돌리지 않아 알린 실패를 받지 못한 채 멈출 수 있다.
     */
    private fun TestScope.collectFailures(): List<PreferenceFailure> =
        mutableListOf<PreferenceFailure>().also { reported -> storeScopes.scopeFor(this).launch { failures.failures.collect { reported += it } } }
}
