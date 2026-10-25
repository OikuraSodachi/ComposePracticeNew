package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 연결할 때([FtpConnector.connect]) 목록을 해석할 수 없는 서버를 걸러 연결을 종료하고 실패 이유를 알리는지 검증하는 통합 테스트.
 * SYST 응답과 해석기 선택은 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpConnectorListingFormatTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = FTPClient().apply { defaultTimeout = ScriptedFtpServer.TIMEOUT_MILLIS }
    /** 연결 상태를 소유한 세션. */
    private val session = FtpSession(client)
    /** 연결에 성공하면 시작되는 Keep-Alive. 테스트가 끝나면 멈춘다. */
    private val keepAlive = FtpKeepAlive(session)
    /** 검증 대상. */
    private val connector = FtpConnector(session, keepAlive)
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runBlocking { keepAlive.stop() }
        runCatching { client.disconnect() }
        server?.close()
    }

    @Test
    fun `MLSD를 지원하지 않는 서버가 SYST를 거부하면 목록 형식을 알 수 없어 연결을 종료한다`() {
        val result = connectTo(mapOf("SYST" to { it.send("502 Command not implemented") }))

        assertEquals(ConnectResult.Failed(ConnectFailureReason.LISTING_FORMAT_UNKNOWN), result)
        assertConnectionClosed()
    }

    @Test
    fun `MLSD를 지원하지 않는 서버가 해석기가 없는 시스템 종류를 알려주면 목록 형식을 알 수 없어 연결을 종료한다`() {
        // "Type: L8"이 붙으면 commons-net이 Unix로 보므로 붙이지 않는다
        val result = connectTo(mapOf("SYST" to { it.send("215 PLAN9") }))

        assertEquals(ConnectResult.Failed(ConnectFailureReason.LISTING_FORMAT_UNKNOWN), result)
        assertConnectionClosed()
    }

    @Test
    fun `MLSD를 지원하는 서버는 SYST를 보내지 않으므로 SYST를 거부해도 연결한다`() {
        val scripted = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "FEAT" to {
                it.send("211-Features:")
                it.send(" MLST type*;size*;modify*;")
                it.send("211 End")
            },
            "SYST" to { it.send("502 Command not implemented") },
        )

        val result = connectTo(scripted)

        assertEquals(ConnectResult.Connected, result)
        assertFalse(server!!.received.toString(), "SYST" in server!!.received)
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `해석기가 있는 시스템 종류를 알려주면 연결하고 이후 목록 조회는 SYST를 다시 보내지 않는다`() {
        val scripted = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "LIST" to { exchange ->
                exchange.data.accept().use { socket ->
                    exchange.send("150 Here comes the directory listing")
                    socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
                }
                exchange.send("226 Transfer complete")
            },
        )

        val result = connectTo(scripted)
        val systSentOnConnect = server!!.received.count { it == "SYST" }
        val listing = runBlocking { FtpCommandExecutor(session).listFiles("ftp://host/r") }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(listOf("ftp://host/r/a.txt"), (listing as IoResult.Success).value.map { it.path })
        assertEquals(systSentOnConnect, server!!.received.count { it == "SYST" })
    }

    @Test
    fun `로그인이 거부되면 목록 형식을 확인하지 않고 연결 실패로 알리며 연결을 남기지 않는다`() {
        val result = connectTo(mapOf("PASS" to { it.send("530 Login incorrect") }))

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertFalse(server!!.received.toString(), "SYST" in server!!.received)
        assertConnectionClosed()
    }

    /**
     * [script]대로 응답하는 서버를 띄워 [FtpConnector]로 접속한다.
     * @param script 기본 응답을 덮어쓸 명령별 응답 동작
     * @return 연결 결과
     */
    private fun connectTo(script: Map<String, (ScriptedFtpServer.Exchange) -> Unit>): ConnectResult {
        val scripted = ScriptedFtpServer(script).also { server = it }
        return runBlocking { connector.connect(scripted.host, scripted.port, "user", "password", "UTF-8") }
    }

    /** 세션이 로그인 상태가 아니고, 클라이언트 소켓이 닫혔고, 서버에 QUIT을 보냈는지 확인한다. */
    private fun assertConnectionClosed() {
        assertFalse(runBlocking { session.isReady() })
        assertFalse(client.isConnected)
        assertTrue(server!!.received.toString(), "QUIT" in server!!.received)
    }
}
