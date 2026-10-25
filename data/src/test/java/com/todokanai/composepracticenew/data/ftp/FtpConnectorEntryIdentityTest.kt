package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 연결할 때([FtpConnector.connect]) MLSD 서버가 항목 식별값(`unique`)을 지원하지만 목록에 담지 않으면 `OPTS MLST`로 요청하고,
 * 그 요청이 거부되거나 연결을 끊어도 연결은 쓸 수 있는지 검증하는 통합 테스트.
 * FEAT 응답 해석과 명령 전송은 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpConnectorEntryIdentityTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = FTPClient().apply { defaultTimeout = ScriptedFtpServer.TIMEOUT_MILLIS }
    /** 연결 상태를 소유한 세션. */
    private val session = FtpSession(client)
    /** 연결에 성공하면 시작되는 Keep-Alive. 테스트가 끝나면 멈춘다. */
    private val keepAlive = FtpKeepAlive(session)
    /** 검증 대상. */
    private val connector = FtpConnector(session, keepAlive)
    /** 서버가 받은 OPTS 인자. 받은 순서대로 쌓는다. */
    private val optsArguments = CopyOnWriteArrayList<String>()
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private val servers = mutableListOf<ScriptedFtpServer>()

    @After
    fun tearDown() {
        runBlocking { keepAlive.stop() }
        runCatching { client.disconnect() }
        servers.forEach { it.close() }
    }

    @Test
    fun `unique를 지원하지만 목록에 담지 않는 서버에는 지금 담는 사실에 unique를 더해 요청한다`() {
        val result = connectTo(mlstFacts = "type*;size*;modify;unique;")

        assertEquals(ConnectResult.Connected, result)
        assertEquals(listOf("MLST type;size;unique;"), optsArguments)
    }

    @Test
    fun `unique를 이미 목록에 담는 서버에는 요청하지 않는다`() {
        val result = connectTo(mlstFacts = "type*;size*;Unique*;")

        assertEquals(ConnectResult.Connected, result)
        assertEquals(emptyList<String>(), optsArguments)
    }

    @Test
    fun `unique를 지원하지 않는 서버에는 요청하지 않는다`() {
        val result = connectTo(mlstFacts = "type*;size*;modify*;")

        assertEquals(ConnectResult.Connected, result)
        assertEquals(emptyList<String>(), optsArguments)
    }

    @Test
    fun `서버가 요청을 거부해도 연결은 그대로 쓴다`() {
        val result = connectTo(mlstFacts = "type*;unique;") { it.send("501 Invalid fact list") }

        assertEquals(ConnectResult.Connected, result)
        assertTrue(runBlocking { session.isReady() })
        assertEquals(1, servers.last().received.count { it == "USER" })
    }

    @Test
    fun `요청에 서버가 연결을 끊으면 다시 연결해 요청 없이 쓴다`() {
        val result = connectTo(mlstFacts = "type*;unique;") {
            it.send("421 Service not available")
            it.hangUp()
        }

        assertEquals(ConnectResult.Connected, result)
        assertTrue(runBlocking { session.isReady() })
        assertEquals("요청이 끊은 연결을 버리고 한 번 더 로그인한다", 2, servers.last().received.count { it == "USER" })
        assertEquals("다시 연결한 뒤에는 요청하지 않는다", 1, optsArguments.size)
    }

    @Test
    fun `LIST 서버에는 요청하지 않는다`() {
        val server = ScriptedFtpServer(mapOf("LIST" to { it.send("550 No files") })).also { servers += it }

        val result = runBlocking { connector.connect(server.host, server.port, "user", "password", "UTF-8") }

        assertEquals(ConnectResult.Connected, result)
        assertFalse(server.received.toString(), "OPTS" in server.received)
    }

    /**
     * FEAT에 MLST와 [mlstFacts]를 알리는 서버에 연결한다.
     * @param mlstFacts FEAT의 MLST 줄에 알릴 사실 목록(예: "type*;size*;unique;")
     * @param onOpts OPTS를 받았을 때의 응답. 인자는 [optsArguments]에 기록한다
     * @return 연결 결과
     */
    private fun connectTo(
        mlstFacts: String,
        onOpts: (ScriptedFtpServer.Exchange) -> Unit = { it.send("200 MLST OPTS ${it.argument.substringAfter(' ')}") }
    ): ConnectResult {
        val script = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "FEAT" to {
                it.send("211-Features:")
                it.send(" MLST $mlstFacts")
                it.send("211 End")
            },
            "OPTS" to { exchange ->
                optsArguments += exchange.argument
                onOpts(exchange)
            },
        )
        val server = ScriptedFtpServer(script).also { servers += it }
        return runBlocking { connector.connect(server.host, server.port, "user", "password", "UTF-8") }
    }
}
