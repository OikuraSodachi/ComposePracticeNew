package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** [PreferencesStore]의 읽기 재시도·실패 시 기본값 방출, 저장 실패 무시 정책, 저장이 호출 순서대로 반영되는지를 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesStoreTest {

    private val key = stringPreferencesKey("k")
    private val stored = preferencesOf(key to "v")

    /** 소비자 코루틴 스코프를 만들고 정리하며, 그 안에서 잡히지 않은 예외를 테스트 실패로 드러내는 규칙. */
    @get:Rule
    val storeScopes = StoreScopeRule()

    @Test
    fun `읽기 IOException 시 기본값을 한 번 방출하고 재시도 후 실제 값을 방출한다`() = runTest {
        val store = PreferencesStore(FakePreferencesDataStore(stored, failingReads = 2), storeScopes.scopeFor(this))
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
    fun `재시도 성공 후에도 Flow가 유지되어 이후 저장을 반영한다`() = runTest {
        val fake = FakePreferencesDataStore(stored, failingReads = 1)
        val store = PreferencesStore(fake, storeScopes.scopeFor(this))
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
        val store = PreferencesStore(FakePreferencesDataStore(stored, failingReads = 1, readError = IllegalStateException("x")), storeScopes.scopeFor(this))
        val result = runCatching { store.data.take(1).toList() }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `저장 IOException은 무시되고 기존 값이 유지된다`() = runTest {
        val fake = FakePreferencesDataStore(stored, writeError = IOException("write"))
        val store = PreferencesStore(fake, storeScopes.scopeFor(this))

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle() // 예외가 잡히지 않으면 StoreScopeRule이 테스트를 실패시킨다

        assertEquals(stored, fake.state.value)
    }

    @Test
    fun `저장 성공 시 값이 반영된다`() = runTest {
        val fake = FakePreferencesDataStore(stored)
        val store = PreferencesStore(fake, storeScopes.scopeFor(this))

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "w"), fake.state.value)
    }

    @Test
    fun `앞선 편집이 반영 중일 때 들어온 편집은 그 뒤에 반영된다`() = runTest {
        // 첫 편집은 반영 전 100ms를 기다린다 — 호출마다 따로 launch하던 이전 구현에서는 두 번째 편집이 먼저 반영되고 첫 편집이 덮어써 "first"가 남는다
        val fake = FakePreferencesDataStore(stored, writeDelays = mutableListOf(100L, 0L))
        val store = PreferencesStore(fake, storeScopes.scopeFor(this))

        store.launchEdit { it[key] = "first" }
        runCurrent() // 소비자가 첫 편집을 꺼내 반영을 시작한 상태
        store.launchEdit { it[key] = "second" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "second"), fake.state.value)
        assertEquals(2, fake.writeCount)
    }

    @Test
    fun `대기열에 함께 쌓인 편집은 한 번의 쓰기로 넣은 순서대로 반영된다`() = runTest {
        val fake = FakePreferencesDataStore(stored)
        val store = PreferencesStore(fake, storeScopes.scopeFor(this))

        store.launchEdit { it[key] = "a" }
        store.launchEdit { it[key] = it[key] + "b" }
        store.launchEdit { it[key] = it[key] + "c" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "abc"), fake.state.value)
        assertEquals(1, fake.writeCount)
    }

    @Test
    fun `저장 실패 후에도 다음 편집을 계속 반영한다`() = runTest {
        val fake = FakePreferencesDataStore(stored, failingWrites = 1)
        val store = PreferencesStore(fake, storeScopes.scopeFor(this))

        store.launchEdit { it[key] = "lost" }
        advanceUntilIdle()
        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "w"), fake.state.value)
    }
}
