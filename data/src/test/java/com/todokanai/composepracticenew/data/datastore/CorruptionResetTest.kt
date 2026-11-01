package com.todokanai.composepracticenew.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.todokanai.composepracticenew.di.DataStoreModule
import com.todokanai.composepracticenew.model.PreferenceFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 손상된 설정 파일을 [DataStoreModule.resettingCorruptionHandler]가 빈 설정으로 바꾸고 초기화를 알리는지, 실제 DataStore와 파일로 검증하는 단위 테스트. */
class CorruptionResetTest {

    /** 손상된 설정 파일을 둘 임시 폴더. */
    @get:Rule
    val folder = TemporaryFolder()

    /** DataStore의 파일 입출력을 돌릴 스코프. 테스트가 끝나면 취소한다. */
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `손상된 파일은 빈 설정으로 바뀌고 초기화를 알린다`() = runBlocking {
        // 길이 5인 필드를 선언하고 끝나는 바이트 — 끝까지 읽지 못해 프로토콜 버퍼 해석이 실패한다(CorruptionException)
        val file = folder.newFile("settings.preferences_pb").apply { writeBytes(byteArrayOf(0x0A, 0x05)) }
        val failures = PreferenceFailureChannel()
        val dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = DataStoreModule.resettingCorruptionHandler(failures),
            scope = storeScope,
            produceFile = { file }
        )

        withTimeout(5_000) {
            assertEquals(emptyPreferences(), dataStore.data.first())
            assertEquals(PreferenceFailure.RESET, failures.failures.first())
        }
    }
}
