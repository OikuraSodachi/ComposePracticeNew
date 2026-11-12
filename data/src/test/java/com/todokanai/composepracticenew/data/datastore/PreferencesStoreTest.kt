package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.usecase.PreferenceFailureUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

/** [PreferencesStore]의 공유 읽기 재시도(타이머·성공 시 즉시)·실패 시 기본값 방출, 읽기·저장 실패 알림, 손상 파일 교체 뒤의 초기화 알림, 저장이 호출 순서대로 하나씩 반영되는지를 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesStoreTest {

    private val key = stringPreferencesKey("k")
    private val stored = preferencesOf(key to "v")

    /** 소비자 코루틴 스코프를 만들고 정리하며, 그 안에서 잡히지 않은 예외를 테스트 실패로 드러내는 규칙. */
    @get:Rule
    val storeScopes = StoreScopeRule()

    /** 검증 대상이 실패를 넣는 알림 목록. */
    private val failures = PreferenceFailureNotices()

    /** 검증 대상이 손상 파일 교체를 확인해 알리는 곳. 테스트는 핸들러를 직접 불러 교체가 일어난 상황을 만든다. */
    private val corruptionReset = CorruptionReset(failures)

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
    fun `재시도를 기다리는 동안 편집이 성공하면 타이머를 기다리지 않고 다시 읽는다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingReads = 2))
        val emitted = mutableListOf<Preferences>()
        val job = launch { store.data.take(2).toList(emitted) }

        advanceTimeBy(1_000 + 500) // 두 번째 실패 뒤 2초 대기 중(타이머는 3초 시점)
        runCurrent()
        store.launchEdit { it[key] = "w" }
        runCurrent()

        assertEquals(listOf(emptyPreferences(), preferencesOf(key to "w")), emitted)
        job.join()
    }

    @Test
    fun `재시도를 기다리는 동안 한 번 읽기가 성공하면 타이머를 기다리지 않고 다시 읽는다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingReads = 2))
        val emitted = mutableListOf<Preferences>()
        val job = launch { store.data.take(2).toList(emitted) }

        advanceTimeBy(1_000 + 500) // 두 번째 실패 뒤 2초 대기 중(타이머는 3초 시점)
        runCurrent()
        assertEquals(stored, store.readOnce())
        runCurrent()

        assertEquals(listOf(emptyPreferences(), stored), emitted)
        job.join()
    }

    @Test
    fun `같은 수집 안에서 회복한 뒤 다시 실패해도 기본값을 방출하고 다시 알리며, 대기는 처음부터 시작한다`() = runTest {
        val fake = FakePreferencesDataStore(stored, failingReads = 3)
        val store = storeOf(fake)
        val reported = collectFailures()
        val emitted = mutableListOf<Preferences>()
        val job = launch { store.data.take(4).toList(emitted) }

        advanceTimeBy(1_000 + 2_000 + 4_000 + 1) // 세 번 실패 뒤 회복 — retryWhen의 attempt는 3까지 올라가 있다
        runCurrent()
        assertEquals(listOf(emptyPreferences(), stored), emitted)

        fake.failActiveCollections(nextReads = 0)
        runCurrent()
        assertEquals(listOf(emptyPreferences(), stored, emptyPreferences()), emitted) // attempt를 이어 세면 기본값 없이 대기만 한다

        advanceTimeBy(1_000 + 1) // 대기가 1초부터 — attempt를 이어 세면 8초를 기다린다
        runCurrent()
        assertEquals(listOf(emptyPreferences(), stored, emptyPreferences(), stored), emitted)
        assertEquals(listOf(PreferenceFailure.READ, PreferenceFailure.READ), reported)
        job.join()
    }

    @Test
    fun `알리기 전에 읽기가 회복하면 읽기 실패는 알리지 않는다`() = runTest {
        // 사건 대기열에 쌓던 이전 구현에서는 화면이 나중에 수집할 때 이미 회복한 읽기 실패를 "기본값으로 보인다"고 알린다
        storeOf(FakePreferencesDataStore(stored, failingReads = 1))
        advanceTimeBy(1_000 + 1) // 화면이 수집하지 않는 동안 실패하고 1초 뒤 회복한다
        runCurrent()

        val reported = collectFailures()
        runCurrent()

        assertEquals(emptyList<PreferenceFailure>(), reported)
    }

    @Test
    fun `읽기 실패가 이어지는 동안 수집을 시작하면 읽기 실패를 알린다`() = runTest {
        storeOf(FakePreferencesDataStore(stored, failingReads = 3))
        advanceTimeBy(500) // 첫 실패 뒤 1초 대기 중
        runCurrent()

        val reported = collectFailures()
        runCurrent()

        assertEquals(listOf(PreferenceFailure.READ), reported)
    }

    @Test
    fun `수집하지 않는 동안 일어난 저장 실패는 수집을 시작하면 알린다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingWrites = 1))
        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        val reported = collectFailures()
        runCurrent()

        assertEquals(listOf(PreferenceFailure.SAVE), reported)
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
    fun `IOException이 아닌 읽기 예외는 공유 수집을 끝내고 스코프로 전파된다`() = runTest {
        storeOf(FakePreferencesDataStore(stored, failingReads = 1, readError = IllegalStateException("x")))
        runCurrent()
        assertTrue(storeScopes.takeUncaught().single() is IllegalStateException)
    }

    @Test
    fun `IOException이 아닌 편집 예외는 소비자를 끝내 스코프로 전파되고, 그 뒤의 편집과 한 번 읽기는 그 예외로 실패한다`() = runTest {
        // StoreScopeRule의 스코프는 예외 처리기가 있어 소비자가 끝나도 테스트가 계속된다 — 대기열이 열린 채 남던 이전 구현에서는 편집이 말없이 버려지고 한 번 읽기가 끝나지 않는다
        val store = storeOf(FakePreferencesDataStore(stored))

        store.launchEdit { throw IllegalStateException("x") }
        runCurrent()

        assertTrue(storeScopes.takeUncaught().single() is IllegalStateException)
        assertTrue(runCatching { store.launchEdit { it[key] = "w" } }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { store.readOnce() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `소비자가 끝날 때 대기열에 남은 한 번 읽기는 기다리지 않고 그 예외로 실패한다`() = runTest {
        // 편집이 반영 전 100ms를 기다리는 동안 한 번 읽기의 장벽이 그 뒤에 쌓인다
        val store = storeOf(FakePreferencesDataStore(stored, writeDelays = mutableListOf(100L)))
        store.launchEdit { throw IllegalStateException("x") }
        val read = async { runCatching { store.readOnce() } }
        runCurrent()

        advanceUntilIdle()

        assertTrue(read.await().exceptionOrNull() is IllegalStateException)
        assertTrue(storeScopes.takeUncaught().single() is IllegalStateException)
    }

    @Test
    fun `한 번 읽기는 지금 값을 돌려준다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored))
        assertEquals(stored, store.readOnce())
    }

    @Test
    fun `한 번 읽기가 실패하면 기본값 대신 null을 돌려주고, 결과로 돌려준 실패는 따로 알리지 않는다`() = runTest {
        val fake = FakePreferencesDataStore(stored)
        val store = storeOf(fake)
        val reported = collectFailures()
        runCurrent() // 공유 수집의 첫 읽기를 마친 뒤 한 번 읽기만 실패시킨다 — 한 번 읽기는 장벽을 기다리는 동안 공유 수집에 차례를 넘긴다
        fake.failNextReads(1)

        assertNull(store.readOnce())
        runCurrent()

        assertEquals(emptyList<PreferenceFailure>(), reported)
    }

    @Test
    fun `반영을 기다리는 편집이 있으면 한 번 읽기는 그 편집이 반영된 뒤의 값을 돌려준다`() = runTest {
        // 편집은 반영 전 100ms를 기다린다 — 대기열을 거치지 않고 바로 읽던 이전 구현에서는 반영 전 값 "v"를 돌려준다
        val store = storeOf(FakePreferencesDataStore(stored, writeDelays = mutableListOf(100L)))

        store.launchEdit { it[key] = "w" }

        assertEquals(preferencesOf(key to "w"), store.readOnce())
    }

    @Test
    fun `앞선 편집이 저장에 실패해도 한 번 읽기는 멈추지 않고 저장된 값을 돌려준다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored, failingWrites = 1))
        val reported = collectFailures()

        store.launchEdit { it[key] = "lost" }

        assertEquals(stored, store.readOnce())
        runCurrent()
        assertEquals(listOf(PreferenceFailure.SAVE), reported)
    }

    @Test
    fun `한 번 읽기는 그 뒤에 호출한 편집을 기다리지 않는다`() = runTest {
        // 첫 편집이 반영된 뒤 들어온 두 번째 편집은 반영 전 100ms를 기다린다 — 읽기가 이를 기다리면 "second"를 돌려준다
        val store = storeOf(FakePreferencesDataStore(stored, writeDelays = mutableListOf(0L, 100L)))

        store.launchEdit { it[key] = "first" }
        val read = async(start = CoroutineStart.UNDISPATCHED) { store.readOnce() } // 두 번째 편집보다 먼저 장벽을 넣도록 바로 시작한다
        store.launchEdit { it[key] = "second" }

        assertEquals(preferencesOf(key to "first"), read.await())
    }

    @Test
    fun `앞선 한 번 읽기의 실패는 Flow 읽기 실패 알림을 막지 않는다`() = runTest {
        val fake = FakePreferencesDataStore(stored)
        val store = storeOf(fake)
        val reported = collectFailures()
        runCurrent() // 공유 수집의 첫 읽기를 마친 뒤 한 번 읽기를 먼저 실패시킨다
        fake.failNextReads(1)

        assertNull(store.readOnce())
        fake.failActiveCollections(0)
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

    @Test
    fun `손상 파일 교체는 핸들러가 불릴 때가 아니라 그 뒤 읽기가 성공할 때 한 번 알린다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored))
        val reported = collectFailures()
        runCurrent() // 공유 수집이 처음 읽기를 마친 상태 — 값이 바뀌지 않으면 다시 읽지 않는다

        corruptionReset.handler.handleCorruption(CorruptionException("corrupt"))
        runCurrent()
        assertEquals(emptyList<PreferenceFailure>(), reported) // 교체 파일을 쓰기 전

        store.readOnce()
        store.readOnce()
        runCurrent()
        assertEquals(listOf(PreferenceFailure.RESET), reported)
    }

    @Test
    fun `교체 파일을 쓰지 못해 핸들러가 거듭 불려도 읽기가 실패하는 동안은 초기화를 알리지 않고, 교체 뒤 한 번만 알린다`() = runTest {
        // DataStore는 교체 쓰기가 실패하면 손상 예외(IOException)를 다시 던진다 — 읽기 실패로 보인다
        val fake = FakePreferencesDataStore(stored)
        val store = storeOf(fake)
        val reported = collectFailures()
        runCurrent() // 공유 수집이 처음 읽기를 마친 상태 — 아래 실패는 한 번 읽기만 겪는다
        fake.failNextReads(2)

        repeat(2) {
            corruptionReset.handler.handleCorruption(CorruptionException("corrupt"))
            assertNull(store.readOnce())
        }
        runCurrent()
        assertEquals(emptyList<PreferenceFailure>(), reported)

        store.readOnce() // 세 번째 시도에서 교체 파일 쓰기가 성공한 상황
        runCurrent()
        assertEquals(listOf(PreferenceFailure.RESET), reported)
    }

    @Test
    fun `손상 파일 교체 뒤 편집이 성공해도 초기화를 알린다`() = runTest {
        val store = storeOf(FakePreferencesDataStore(stored))
        val reported = collectFailures()
        runCurrent() // 공유 수집이 처음 읽기를 마친 상태 — 아래 초기화 알림은 편집 성공에서 나온다

        corruptionReset.handler.handleCorruption(CorruptionException("corrupt"))
        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(listOf(PreferenceFailure.RESET), reported)
    }

    /** [fake] 위에 검증 대상을 만든다. */
    private fun TestScope.storeOf(fake: FakePreferencesDataStore) = PreferencesStore(fake, storeScopes.scopeFor(this), failures, corruptionReset)

    /**
     * 화면이 받는 것과 같은 경로([PreferenceFailureUseCase.deliver])로 알린 실패를 테스트가 끝날 때까지 모아 두는 목록을 돌려준다.
     * 수집은 backgroundScope가 아니라 소비자와 같은 스코프에서 한다 — advanceUntilIdle은 background 작업을 끝까지 돌리지 않아 알린 실패를 받지 못한 채 멈출 수 있다.
     */
    private fun TestScope.collectFailures(): List<PreferenceFailure> =
        mutableListOf<PreferenceFailure>().also { reported -> storeScopes.scopeFor(this).launch { PreferenceFailureUseCase(failures).deliver { reported += it } } }
}
