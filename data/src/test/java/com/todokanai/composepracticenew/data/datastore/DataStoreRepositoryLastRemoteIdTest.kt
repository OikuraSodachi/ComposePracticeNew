package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [DataStoreRepository.clearLastRemoteIdIf]가 직전 저장이 반영된 값을 기준으로 확인·초기화를 한 번에 처리하는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreRepositoryLastRemoteIdTest {

    private val key = DataStoreRepository.DATASTORE_LAST_REMOTE_ID
    private val scopes = mutableListOf<CoroutineScope>()

    /** 이 테스트의 스케줄러를 공유하는 소비자 스코프를 만들고 정리 대상으로 등록한다. */
    private fun TestScope.newStoreScope(): CoroutineScope = storeScope().also { scopes += it }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `저장 직후 같은 id로 초기화하면 저장이 늦게 도착해도 초기화된다`() = runTest {
        // 저장 편집이 반영 전 100ms를 기다린다 — 확인을 편집 밖에서 하던 이전 구현에서는 저장 전 값을 보고 초기화를 건너뛰어 id가 남는다
        val fake = FakePreferencesDataStore(preferencesOf(), writeDelays = mutableListOf(100L, 0L))
        val repo = DataStoreRepository(PreferencesStore(fake, newStoreScope()))

        repo.setLastRemoteId(5L)
        repo.clearLastRemoteIdIf(5L)
        advanceUntilIdle()

        assertNull(fake.state.value[key])
    }

    @Test
    fun `저장된 id가 다르면 초기화하지 않는다`() = runTest {
        val fake = FakePreferencesDataStore(preferencesOf(key to 5L))
        val repo = DataStoreRepository(PreferencesStore(fake, newStoreScope()))

        repo.clearLastRemoteIdIf(7L)
        advanceUntilIdle()

        assertEquals(5L, fake.state.value[key])
    }
}
