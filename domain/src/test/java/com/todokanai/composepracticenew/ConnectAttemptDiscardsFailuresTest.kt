package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureAlert
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

/** [RemoteConnectionUseCase.connectAndNavigate]가 연결 시도를 시작할 때마다 아직 알리지 않은 이전 시도의 실패를 빼게 하는지 검증하는 단위 테스트(#371). */
class ConnectAttemptDiscardsFailuresTest {

    /** 연결 시작과 맡긴 실패를 기록하는 알림함. */
    private val alerts = RecordingConnectFailureAlerts()

    @Test
    fun `연결 시도를 시작하면 이전 실패를 빼게 한다`() {
        runBlocking { useCaseAnswering(ConnectResult.Connected).connectAndNavigate(itemWithPort(21)) }

        assertEquals(listOf<ConnectFailureAlert?>(null), alerts.events)
    }

    @Test
    fun `실패한 연결 시도도 시작할 때 이전 실패를 빼게 한다`() {
        runBlocking { useCaseAnswering(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)).connectAndNavigate(itemWithPort(21)) }

        assertEquals(listOf<ConnectFailureAlert?>(null), alerts.events)
    }

    @Test
    fun `포트 오류로 접속하지 않는 시도도 시작할 때 이전 실패를 빼게 한다`() {
        runBlocking { useCaseAnswering(ConnectResult.Connected).connectAndNavigate(itemWithPort(0)) }

        assertEquals(listOf<ConnectFailureAlert?>(null), alerts.events)
    }

    /**
     * 연결 요청에 [result]로 답하는 검증 대상을 만든다.
     * @param result 연결 요청에 돌려줄 결과
     * @return 검증 대상
     */
    private fun useCaseAnswering(result: ConnectResult) =
        RemoteConnectionUseCase(FakeRemoteConnection(result), FakeRootNavigator(), NoOpLastConnection, alerts)

    /**
     * [port]만 다른 접속 정보를 만든다.
     * @param port 접속 정보의 포트
     * @return 접속 정보
     */
    private fun itemWithPort(port: Int) = RemoteStorageItem(name = "nas", address = "192.168.0.1", port = port, userId = "", password = "", settings = RemoteProtocolSettings.Ftp("UTF-8"))

    /** 이 테스트는 마지막 연결 기록을 쓰지 않으므로 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpLastConnection : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = SavedRemoteId.None
        override fun setLastRemoteId(id: Long?) {}
        override fun clearLastRemoteIdIf(id: Long) {}
    }
}
