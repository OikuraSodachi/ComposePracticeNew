package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** [PreferencesStore]의 읽기 재시도·실패 시 기본값 방출과 저장 실패 무시 정책을 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesStoreTest {

    private val key = stringPreferencesKey("k")
    private val stored = preferencesOf(key to "v")

    /**
     * 테스트용 DataStore. 앞선 [failingReads]회의 수집은 [readError]를 던지고, 이후에는 [state]를 방출한다.
     * @param initial 초기 저장값
     * @param failingReads 실패시킬 수집 횟수
     * @param readError 읽기 실패 시 던질 예외
     * @param writeError null이 아니면 updateData가 이 예외를 던진다
     */
    private class FakeDataStore(
        initial: Preferences,
        private var failingReads: Int = 0,
        private val readError: Throwable = IOException("read"),
        private val writeError: Throwable? = null
    ) : DataStore<Preferences> {
        val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = flow {
            if (failingReads > 0) {
                failingReads--
                throw readError
            }
            state.collect { emit(it) }
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writeError?.let { throw it }
            return transform(state.value).also { state.value = it }
        }
    }

    @Test
    fun `읽기 IOException 시 기본값을 한 번 방출하고 재시도 후 실제 값을 방출한다`() = runTest {
        val store = PreferencesStore(FakeDataStore(stored, failingReads = 2), this)
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
        val fake = FakeDataStore(stored, failingReads = 1)
        val store = PreferencesStore(fake, this)
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
        val store = PreferencesStore(FakeDataStore(stored, failingReads = 1, readError = IllegalStateException("x")), this)
        val result = runCatching { store.data.take(1).toList() }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `저장 IOException은 무시되고 기존 값이 유지된다`() = runTest {
        val fake = FakeDataStore(stored, writeError = IOException("write"))
        val store = PreferencesStore(fake, this)

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle() // 예외가 전파되면 runTest가 실패한다

        assertEquals(stored, fake.state.value)
    }

    @Test
    fun `저장 성공 시 값이 반영된다`() = runTest {
        val fake = FakeDataStore(stored)
        val store = PreferencesStore(fake, this)

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "w"), fake.state.value)
    }
}
