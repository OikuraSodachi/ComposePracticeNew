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

/** [RemoteConnectionUseCase.connectAndNavigate]가 포트가 범위를 벗어난 접속 정보로는 연결을 시도하지 않는지 검증하는 단위 테스트. */
class RemoteConnectionPortCheckTest {

    /** 연결 요청을 기록하는 가짜 연결 저장소. */
    private val connection = FakeRemoteConnection()
    /** 검증 대상. */
    private val useCase = RemoteConnectionUseCase(connection, FakeNavigator(), NoOpLastConnectionRepository)

    @Test
    fun `포트가 범위를 벗어나면 연결을 시도하지 않고 연결 실패로 알린다`() {
        listOf(0, 65536, -1).forEach { port ->
            val result = runBlocking { useCase.connectAndNavigate(itemWithPort(port)) }

            assertEquals("port=$port", ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        }
        assertEquals(emptyList<Int>(), connectedPorts())
    }

    @Test
    fun `포트가 범위 안이면 연결을 시도한다`() {
        listOf(1, 21, 65535).forEach { port -> runBlocking { useCase.connectAndNavigate(itemWithPort(port)) } }

        assertEquals(listOf(1, 21, 65535), connectedPorts())
    }

    /**
     * [port]만 다른 접속 정보를 만든다.
     * @param port 접속 정보의 포트
     * @return 접속 정보
     */
    private fun itemWithPort(port: Int) = RemoteStorageItem(name = "nas", address = "192.168.0.1", port = port, userId = "", password = "", settings = RemoteProtocolSettings.Ftp("UTF-8"))

    /** 연결을 요청받은 포트. 받은 순서대로다. */
    private fun connectedPorts(): List<Int> = connection.connected.map { it.port }

    /** 포트 검사가 마지막 연결 기록과 무관함을 전제로 한, 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpLastConnectionRepository : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = SavedRemoteId.None
        override fun setLastRemoteId(id: Long?) {}
        override fun clearLastRemoteIdIf(id: Long) {}
    }
}
