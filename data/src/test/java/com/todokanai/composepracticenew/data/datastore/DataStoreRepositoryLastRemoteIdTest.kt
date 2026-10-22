package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** [DataStoreRepository.clearLastRemoteIdIf]가 직전 저장이 반영된 값을 기준으로 확인·초기화를 한 번에 처리하는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreRepositoryLastRemoteIdTest {

    /** 검증 대상인 마지막 접속 서버 id 키. */
    private val key = DataStoreRepository.DATASTORE_LAST_REMOTE_ID

    /** 소비자 코루틴 스코프를 만들고 정리하며, 그 안에서 잡히지 않은 예외를 테스트 실패로 드러내는 규칙. */
    @get:Rule
    val storeScopes = StoreScopeRule()

    @Test
    fun `저장 직후 같은 id로 초기화하면 저장이 늦게 도착해도 초기화된다`() = runTest {
        // 저장 편집이 반영 전 100ms를 기다린다 — 확인을 편집 밖에서 하던 이전 구현에서는 저장 전 값을 보고 초기화를 건너뛰어 id가 남는다
        val fake = FakePreferencesDataStore(preferencesOf(), writeDelays = mutableListOf(100L, 0L))
        val repo = DataStoreRepository(PreferencesStore(fake, storeScopes.scopeFor(this)))

        repo.setLastRemoteId(5L)
        runCurrent() // 저장이 반영되는 중(100ms 대기)에 초기화를 요청한다
        repo.clearLastRemoteIdIf(5L)
        advanceUntilIdle()

        assertNull(fake.state.value[key])
    }

    @Test
    fun `저장된 id가 다르면 초기화하지 않는다`() = runTest {
        val fake = FakePreferencesDataStore(preferencesOf(key to 5L))
        val repo = DataStoreRepository(PreferencesStore(fake, storeScopes.scopeFor(this)))

        repo.clearLastRemoteIdIf(7L)
        advanceUntilIdle()

        assertEquals(5L, fake.state.value[key])
    }
}
