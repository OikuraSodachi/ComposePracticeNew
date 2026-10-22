package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** [PreferencesStore]의 읽기 재시도·실패 시 기본값 방출, 저장 실패 무시 정책, 저장이 호출 순서대로 반영되는지를 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesStoreTest {

    private val key = stringPreferencesKey("k")
    private val stored = preferencesOf(key to "v")

    /** 테스트마다 만든 소비자 스코프. 끝나지 않는 소비자 코루틴을 테스트 종료 시 정리한다. */
    private val scopes = mutableListOf<CoroutineScope>()

    /** 이 테스트의 스케줄러를 공유하는 소비자 스코프를 만들고 정리 대상으로 등록한다. */
    private fun TestScope.newStoreScope(): CoroutineScope = storeScope().also { scopes += it }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `읽기 IOException 시 기본값을 한 번 방출하고 재시도 후 실제 값을 방출한다`() = runTest {
        val store = PreferencesStore(FakePreferencesDataStore(stored, failingReads = 2), newStoreScope())
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
        val store = PreferencesStore(fake, newStoreScope())
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
        val store = PreferencesStore(FakePreferencesDataStore(stored, failingReads = 1, readError = IllegalStateException("x")), newStoreScope())
        val result = runCatching { store.data.take(1).toList() }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `저장 IOException은 무시되고 기존 값이 유지된다`() = runTest {
        val fake = FakePreferencesDataStore(stored, writeError = IOException("write"))
        val store = PreferencesStore(fake, newStoreScope())

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle() // 예외가 전파되면 runTest가 실패한다

        assertEquals(stored, fake.state.value)
    }

    @Test
    fun `저장 성공 시 값이 반영된다`() = runTest {
        val fake = FakePreferencesDataStore(stored)
        val store = PreferencesStore(fake, newStoreScope())

        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "w"), fake.state.value)
    }

    @Test
    fun `연속 저장은 먼저 호출한 편집이 늦게 도착하더라도 호출 순서대로 반영된다`() = runTest {
        // 첫 편집은 반영 전 100ms를 기다린다 — 호출마다 따로 launch하던 이전 구현에서는 두 번째 편집이 먼저 반영되고 첫 편집이 덮어써 "first"가 남는다
        val fake = FakePreferencesDataStore(stored, writeDelays = mutableListOf(100L, 0L))
        val store = PreferencesStore(fake, newStoreScope())

        store.launchEdit { it[key] = "first" }
        store.launchEdit { it[key] = "second" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "second"), fake.state.value)
    }

    @Test
    fun `저장 실패 후에도 다음 편집을 계속 반영한다`() = runTest {
        val fake = FakePreferencesDataStore(stored, failingWrites = 1)
        val store = PreferencesStore(fake, newStoreScope())

        store.launchEdit { it[key] = "lost" }
        store.launchEdit { it[key] = "w" }
        advanceUntilIdle()

        assertEquals(preferencesOf(key to "w"), fake.state.value)
    }
}
