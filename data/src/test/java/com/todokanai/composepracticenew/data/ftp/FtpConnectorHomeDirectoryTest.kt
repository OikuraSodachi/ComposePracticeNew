package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 연결할 때([FtpConnector.connect]) 로그인 직후의 현재 디렉터리를 세션의 홈과 현재 디렉터리로 기록하는지 검증하는 통합 테스트.
 * 홈은 대상 밖으로 나가는 이동이 거부될 때 다시 시도할 곳이다([FtpCommandExecutor]). PWD 응답 해석은 commons-net 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpConnectorHomeDirectoryTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = SessionFtpClient()
    /** 연결 상태와 홈을 소유한 세션. 서버 스크립트가 응답하지 않을 때 오래 기다리지 않도록 I/O 상한을 짧게 둔다. */
    private val session = FtpSession(client, ConnectionLossNotices(), ioTimeoutMillis = ScriptedFtpServer.TIMEOUT_MILLIS)
    /** Keep-Alive 루프를 돌리는 스코프. 테스트가 끝나면 취소한다. */
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 연결에 성공하면 시작되는 Keep-Alive. 테스트가 끝나면 멈춘다. */
    private val keepAlive = FtpKeepAlive(session, session, keepAliveScope)
    /** 검증 대상. */
    private val connector = FtpConnector(session, keepAlive)
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runBlocking { keepAlive.stop() }
        keepAliveScope.cancel()
        runCatching { client.disconnect() }
        server?.close()
    }

    @Test
    fun `로그인 직후의 현재 디렉터리를 세션의 홈과 현재 디렉터리로 기록한다`() {
        val result = connectTo(mapOf("PWD" to { it.send("257 \"/home/u\" is the current directory") }))

        assertEquals(ConnectResult.Connected, result)
        assertEquals("/home/u", client.homeDirectory)
        assertEquals("/home/u", client.workingDirectory)
    }

    @Test
    fun `서버가 PWD를 거부해도 연결하고 홈은 모르는 채로 둔다`() {
        val result = connectTo(mapOf("PWD" to { it.send("502 Command not implemented") }))

        assertEquals(ConnectResult.Connected, result)
        assertNull(client.homeDirectory)
        assertNull(client.workingDirectory)
    }

    @Test
    fun `다시 연결하면 이전 연결에서 이동한 위치를 버리고 새 연결의 홈을 현재 디렉터리로 기록한다`() {
        connectTo(mapOf("PWD" to { it.send("257 \"/home/${it.connectionIndex}\" is the current directory") }))
        runBlocking { session.withConnectedClient { it.changeDirectory("/r"); IoResult.Done } }
        assertEquals("/r", client.workingDirectory)

        runBlocking { connector.disconnect() }
        val result = connect(server!!)

        assertEquals(ConnectResult.Connected, result)
        assertEquals("/home/1", client.homeDirectory)
        assertEquals("/home/1", client.workingDirectory)
    }

    /**
     * [script]대로 응답하는 서버를 띄워 [FtpConnector]로 접속한다.
     * @param script 기본 응답을 덮어쓸 명령별 응답 동작
     * @return 연결 결과
     */
    private fun connectTo(script: Map<String, (ScriptedFtpServer.Exchange) -> Unit>): ConnectResult =
        connect(ScriptedFtpServer(script).also { server = it })

    /**
     * [scripted]에 [FtpConnector]로 접속한다.
     * @param scripted 접속할 서버
     * @return 연결 결과
     */
    private fun connect(scripted: ScriptedFtpServer): ConnectResult =
        runBlocking { connector.connect("server", scripted.host, scripted.port, "user", "password", "UTF-8") }
}
