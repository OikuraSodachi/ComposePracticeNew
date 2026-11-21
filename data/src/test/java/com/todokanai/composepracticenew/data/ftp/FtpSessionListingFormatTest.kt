package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 세션을 열 때([FtpSession.open]) 목록을 해석할 수 없는 서버를 걸러 연결을 종료하고 실패 이유를 알리는지 검증하는 통합 테스트.
 * SYST 응답과 해석기 선택은 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpSessionListingFormatTest {

    /** 세션 관리자로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture()
    /** 마지막으로 띄운 서버. */
    private lateinit var server: ScriptedFtpServer

    @After
    fun tearDown() = fixture.close()

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
        assertFalse(server.received.toString(), "SYST" in server.received)
        assertNotNull(fixture.manager.current())
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
        val systSentOnConnect = server.received.count { it == "SYST" }
        val listing = runBlocking { fixture.manager.withBrowsingSession { it.listing.listFiles("ftp://host/r") } }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(listOf("ftp://host/r/a.txt"), (listing as IoResult.Success).value.map { (it as ListedItem.Entry).entry.path })
        assertEquals(systSentOnConnect, server.received.count { it == "SYST" })
    }

    @Test
    fun `로그인이 거부되면 목록 형식을 확인하지 않고 로그인 거부로 알리며 연결을 남기지 않는다`() {
        val result = connectTo(mapOf("PASS" to { it.send("530 Login incorrect") }))

        assertEquals(ConnectResult.Failed(ConnectFailureReason.LOGIN_REFUSED), result)
        assertFalse(server.received.toString(), "SYST" in server.received)
        assertConnectionClosed()
    }

    @Test
    fun `서버가 바이너리 전송 방식을 거부하면 목록 형식을 확인하지 않고 연결을 종료한다`() {
        val result = connectTo(mapOf("TYPE" to { it.send("504 Command not implemented for that parameter") }))

        assertEquals(ConnectResult.Failed(ConnectFailureReason.BINARY_TYPE_REFUSED), result)
        assertFalse(server.received.toString(), "SYST" in server.received)
        assertConnectionClosed()
    }

    @Test
    fun `서버가 접속 인사로 421을 보내면 연결 실패로 알린다`() {
        server = fixture.server(greeting = { "421 Too many connections" })

        val result = fixture.connect(server)

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertNull(fixture.manager.current())
        assertFalse(fixture.clients.single().isConnected)
    }

    @Test
    fun `소켓을 만들기 전에 닫은 세션은 그 뒤 서버에 접속하지 않는다`() {
        // 호스트 이름을 찾는 중처럼 닫을 소켓이 아직 없을 때 닫혀도, 뒤이은 접속이 끝까지 진행되지 않아야 한다
        server = fixture.server()
        val session = FtpSession(SessionFtpClient(), ioTimeoutMillis = ScriptedFtpServer.TIMEOUT_MILLIS)
        runBlocking { session.close() }

        val opening = runBlocking { session.open(FtpEndpoint(server.host, server.port, "user", "password", "UTF-8")) }

        assertEquals(SessionOpening.Closed, opening)
        assertEquals(0, server.connectionCount)
    }

    /**
     * [script]대로 응답하는 서버를 띄워 접속한다.
     * @param script 기본 응답을 덮어쓸 명령별 응답 동작
     * @return 연결 결과
     */
    private fun connectTo(script: Map<String, (ScriptedFtpServer.Exchange) -> Unit>): ConnectResult {
        server = fixture.server(script)
        return fixture.connect(server)
    }

    /** 연결된 세션이 없고, 클라이언트 소켓이 닫혔고, 서버에 QUIT을 보냈는지 확인한다. */
    private fun assertConnectionClosed() {
        assertNull(fixture.manager.current())
        assertFalse(fixture.clients.single().isConnected)
        assertTrue(server.received.toString(), "QUIT" in server.received)
    }
}
