package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SavedRemoteId
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RemoteConnectionUseCase.connectAndNavigate]가 연결에 성공했을 때만 연결한 서버의 루트로 이동시키는지 검증하는 단위 테스트(#229, #445).
 */
class RemoteRootNavigationTest {

    /** 검증에 쓰는 접속 정보. */
    private val nas = RemoteStorageItem(id = 5L, name = "nas", address = "192.168.0.1", port = 21, userId = "", password = "", settings = RemoteProtocolSettings.Ftp("UTF-8"))

    @Test
    fun `연결에 성공하면 연결한 서버의 루트로 이동한다`() {
        val rootNavigator = FakeRootNavigator()

        val result = runBlocking { RemoteConnectionUseCase(FakeRemoteConnection(), rootNavigator, NoOpLastConnection, RecordingConnectFailureAlerts()).connectAndNavigate(nas) }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(listOf(nas), rootNavigator.navigated)
    }

    @Test
    fun `연결에 실패하면 이동하지 않는다`() {
        val rootNavigator = FakeRootNavigator()
        val failed = ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)

        runBlocking { RemoteConnectionUseCase(FakeRemoteConnection(failed), rootNavigator, NoOpLastConnection, RecordingConnectFailureAlerts()).connectAndNavigate(nas) }

        assertEquals(emptyList<RemoteStorageItem>(), rootNavigator.navigated)
    }

    /** 이동 검증이 마지막 연결 기록과 무관함을 전제로 한, 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpLastConnection : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = SavedRemoteId.None
        override fun setLastRemoteId(id: Long?) {}
        override fun clearLastRemoteIdIf(id: Long) {}
    }
}
