package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.UnknownHostException

/**
 * 탐색 세션의 접속이 실패한 원인을 연결 실패 이유로 나눠 알리는지 검증한다(#408).
 * 시간 초과·접속 거부는 실제 소켓으로, 이름 찾기·경로 실패는 테스트 환경의 네트워크에 기대지 않도록 예외를 던지는 클라이언트로 재현한다.
 */
class FtpConnectFailureReasonTest {

    /** 세션 관리자로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture()

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `서버가 접속 인사를 보내지 않으면 시간 초과로 알린다`() {
        val server = fixture.server(greeting = { null })

        assertEquals(ConnectResult.Failed(ConnectFailureReason.TIMED_OUT), fixture.connect(server))
    }

    @Test
    fun `아무도 받지 않는 포트에 접속하면 접속할 수 없음으로 알린다`() {
        val server = fixture.server()
        val closedPort = ServerSocket(0).use { it.localPort }

        assertEquals(ConnectResult.Failed(ConnectFailureReason.UNREACHABLE), connect(fixture.itemOf(server).copy(port = closedPort)))
    }

    @Test
    fun `호스트를 찾지 못하면 호스트 없음으로 알린다`() {
        assertEquals(ConnectResult.Failed(ConnectFailureReason.HOST_NOT_FOUND), connectFailingWith(UnknownHostException("nowhere")))
    }

    @Test
    fun `호스트로 가는 경로가 없으면 접속할 수 없음으로 알린다`() {
        assertEquals(ConnectResult.Failed(ConnectFailureReason.UNREACHABLE), connectFailingWith(NoRouteToHostException("Host unreachable")))
    }

    @Test
    fun `나눌 수 없는 입출력 실패는 연결 실패로 알린다`() {
        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), connectFailingWith(IOException("broken")))
    }

    /**
     * 접속할 때 [cause]를 던지는 클라이언트로 접속한다.
     * @param cause 접속에서 던질 예외
     * @return 연결 결과
     */
    private fun connectFailingWith(cause: IOException): ConnectResult {
        fixture.clientFactory = { FailingConnectClient(cause) }
        return connect(fixture.itemOf(fixture.server()))
    }

    /**
     * 저장된 서버 정보 [item]으로 탐색 세션에 접속한다.
     * @param item 접속할 서버 정보
     * @return 연결 결과
     */
    private fun connect(item: RemoteStorageItem): ConnectResult =
        runBlocking { fixture.manager.connect(item) }

    /**
     * 서버에 접속(소켓 열기)할 때 정한 예외를 던지는 FTP 클라이언트.
     * @param cause 접속에서 던질 예외
     */
    private class FailingConnectClient(private val cause: IOException) : SessionFtpClient() {
        override fun connect(hostname: String, port: Int) {
            throw cause
        }
    }
}
