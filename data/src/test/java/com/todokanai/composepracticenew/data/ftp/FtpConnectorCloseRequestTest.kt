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
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 앱의 종료 요청([FtpSession.closeBypassingIoLock])이 연결 작업([FtpConnector.connect])이나 진행 중인 목록 조회와 겹쳐도
 * 연결 성공으로 뒤집히지 않고 명령을 연결 끊김으로 끝내는지, 그리고 LIST 서버의 목록 조회 명령 형태를 검증하는 통합 테스트.
 * 소켓을 닫을 때의 예외와 명령 인자는 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpConnectorCloseRequestTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. 명령을 보내기 직전에 테스트가 끼어들 수 있다. */
    private val client = InterceptingFtpClient().apply { defaultTimeout = ScriptedFtpServer.TIMEOUT_MILLIS }
    /** 연결 상태를 소유한 세션. */
    private val session = FtpSession(client)
    /** 연결에 성공하면 시작되는 Keep-Alive. 테스트가 끝나면 멈춘다. */
    private val keepAlive = FtpKeepAlive(session)
    /** 검증 대상. */
    private val connector = FtpConnector(session, keepAlive)
    /** 서버가 받은 LIST 인자. 받은 순서대로 쌓는다. */
    private val listArguments = CopyOnWriteArrayList<String>()
    /** 서버가 받은 CWD 인자. 받은 순서대로 쌓는다. */
    private val cwdArguments = CopyOnWriteArrayList<String>()
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private val servers = mutableListOf<ScriptedFtpServer>()

    @After
    fun tearDown() {
        runBlocking { keepAlive.stop() }
        runCatching { client.disconnect() }
        servers.forEach { it.close() }
    }

    @Test
    fun `LIST 서버에 연결할 때는 목록을 조회하지 않는다`() {
        val result = connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(ConnectResult.Connected, result)
        assertFalse(servers.last().received.toString(), "LIST" in servers.last().received)
    }

    @Test
    fun `LIST 서버의 목록은 디렉터리로 이동한 뒤 인자 없는 LIST로 조회한다`() {
        connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals(listOf("/r"), cwdArguments)
        assertEquals("경로 인자도 -a 같은 옵션도 보내지 않는다", listOf(""), listArguments)
    }

    @Test
    fun `옵션 요청 중 앱이 연결 종료를 요청해 연결이 끊기면 다시 연결하지 않고 실패로 알린다`() {
        val mark = session.closeRequestMark()
        val closer = AtomicReference<Thread?>()
        val result = connectToMlsdServer { exchange ->
            closer.set(thread { runBlocking { session.closeBypassingIoLock() } })
            awaitUntil("종료 요청이 표시되지") { session.isCloseRequestedSince(mark) }
            exchange.send("421 Service not available, closing control connection")
            exchange.hangUp()
        }
        closer.get()?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertEquals("종료 요청을 다시 연결로 뒤집지 않는다", 1, servers.last().received.count { it == "USER" })
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `옵션 요청 직전 종료 요청으로 닫힌 연결에 명령을 보내지 못해 요청이 끝나도 연결 성공으로 알리지 않는다`() {
        val closer = AtomicReference<Thread?>()
        client.beforeCommand = { command ->
            // OPTS를 보내기 직전에 종료를 요청해, 보내지 못한 실패가 응답을 기다리지 않는 실패로 끝나게 한다
            if (command == "OPTS") {
                closer.set(thread { runBlocking { session.closeBypassingIoLock() } })
                awaitUntil("제어 연결 소켓이 닫히지") { client.isControlSocketClosed }
            }
        }
        val result = connectToMlsdServer { it.send("200 MLST OPTS") }
        closer.get()?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertEquals("종료 요청을 다시 연결로 뒤집지 않는다", 1, servers.last().received.count { it == "USER" })
        assertFalse(runBlocking { session.isReady() })
        assertFalse(client.isConnected)
    }

    @Test
    fun `목록을 받는 중 종료를 요청하면 데이터 연결까지 닫아 명령을 연결 끊김으로 끝낸다`() {
        val dataClosedByClient = AtomicBoolean(false)
        val closer = AtomicReference<Thread?>()
        connectToListServer { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Here comes the directory listing")
                socket.getOutputStream().write(listingLines("a.txt").toByteArray())
                closer.set(thread { runBlocking { session.closeBypassingIoLock() } })
                // 전송을 끝내지 않고 클라이언트가 데이터 연결을 닫을 때까지 기다린다
                socket.soTimeout = ScriptedFtpServer.TIMEOUT_MILLIS
                dataClosedByClient.set(socket.getInputStream().read() == -1)
            }
        }

        val listing = runBlocking { FtpCommandExecutor(session).listFiles("ftp://host/r") }
        closer.get()?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertTrue(listing.toString(), listing is IoResult.ConnectionLost)
        assertTrue("데이터 연결을 닫아 전송을 기다리지 않는다", dataClosedByClient.get())
        assertFalse("진행 중인 명령이 있으면 다른 스레드에서 QUIT을 보내지 않는다", "QUIT" in servers.last().received)
        assertFalse(client.isConnected)
    }

    @Test
    fun `진행 중인 명령이 없을 때 종료를 요청하면 로그아웃한 뒤 연결을 닫는다`() {
        connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        runBlocking { session.closeBypassingIoLock() }

        assertEquals("QUIT", servers.last().received.last())
        assertFalse(runBlocking { session.isReady() })
        assertFalse(client.isConnected)
    }

    /**
     * LIST에 [onList]대로 응답하는 LIST 서버(MLSD 미지원)를 띄워 [FtpConnector]로 접속한다. 받은 LIST·CWD 인자는 [listArguments]·[cwdArguments]에 쌓는다.
     * @param onList LIST를 받았을 때의 응답 동작
     * @return 연결 결과
     */
    private fun connectToListServer(onList: (ScriptedFtpServer.Exchange) -> Unit): ConnectResult {
        val script = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "CWD" to { exchange ->
                cwdArguments += exchange.argument
                exchange.send("250 Directory changed")
            },
            "LIST" to { exchange ->
                listArguments += exchange.argument
                onList(exchange)
            },
        )
        val server = ScriptedFtpServer(script).also { servers += it }
        return runBlocking { connector.connect(server.host, server.port, "user", "password", "UTF-8") }
    }

    /**
     * `unique`를 지원하지만 목록에 담지 않는 MLSD 서버를 띄워 [FtpConnector]로 접속한다. 연결할 때 `OPTS MLST`를 보내게 된다.
     * @param onOpts OPTS를 받았을 때의 응답 동작
     * @return 연결 결과
     */
    private fun connectToMlsdServer(onOpts: (ScriptedFtpServer.Exchange) -> Unit): ConnectResult {
        val script = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "FEAT" to {
                it.send("211-Features:")
                it.send(" MLST type*;size*;unique;")
                it.send("211 End")
            },
            "OPTS" to onOpts,
        )
        val server = ScriptedFtpServer(script).also { servers += it }
        return runBlocking { connector.connect(server.host, server.port, "user", "password", "UTF-8") }
    }

    /**
     * 연결한 세션으로 [path]의 화면 목록을 조회해 항목 이름을 돌려준다.
     * @param path ftp://server/path 형식의 디렉터리 경로
     * @return 항목 이름 집합
     */
    private fun listedNamesOf(path: String): Set<String> {
        val listing = runBlocking { FtpCommandExecutor(session).listFiles(path) }
        return (listing as IoResult.Success).value.mapTo(HashSet()) { it.name }
    }

    /**
     * 데이터 연결로 [names]를 유닉스 형식 목록으로 보내고 226으로 끝낸다.
     * @param exchange 응답 중인 서버 쪽 연결
     * @param names 보낼 항목 이름
     */
    private fun sendListing(exchange: ScriptedFtpServer.Exchange, vararg names: String) {
        exchange.data.accept().use { socket ->
            exchange.send("150 Here comes the directory listing")
            socket.getOutputStream().write(listingLines(*names).toByteArray())
        }
        exchange.send("226 Transfer complete")
    }

    /**
     * [names]를 유닉스 형식 목록 줄로 만든다. 모두 1바이트 파일이다.
     * @param names 항목 이름
     * @return CRLF로 끝나는 목록 줄을 이은 문자열
     */
    private fun listingLines(vararg names: String): String =
        names.joinToString("") { name -> "-rw-r--r--   1 user group        1 Jan 01 00:00 $name\r\n" }

    /**
     * [condition]이 참이 될 때까지 기다린다. 검증 대상이 회귀해 조건이 끝내 참이 되지 않아도 테스트가 멈추지 않도록,
     * 기한([ScriptedFtpServer.TIMEOUT_MILLIS])이 지나면 실패로 끝낸다.
     * @param description 실패 메시지에 담을, 기다린 조건의 설명(예: "소켓이 닫히지")
     * @param condition 기다릴 조건
     */
    private fun awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("기한 안에 $description 않았다")
            Thread.yield()
        }
    }

    /** 명령을 보내기 직전에 테스트가 끼어들 수 있는 실제 FTP 클라이언트. 연결 단계 사이에 종료 요청을 끼워 넣는 데 쓴다. */
    private class InterceptingFtpClient : FTPClient() {
        /** 명령을 보내기 직전에 실행할 동작. 보낼 명령 이름을 받는다. 명령을 보내는 스레드에서 실행된다. */
        @Volatile var beforeCommand: (String) -> Unit = {}
        /** 제어 연결 소켓이 닫혔는지(소켓이 없으면 닫힌 것으로 본다). */
        val isControlSocketClosed: Boolean get() = _socket_?.isClosed ?: true

        override fun sendCommand(command: String, args: String?): Int {
            beforeCommand(command)
            return super.sendCommand(command, args)
        }
    }
}
