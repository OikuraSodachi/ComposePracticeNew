package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 앱의 종료 요청([FtpSessionManager.disconnect])·끊김 종료([FtpSessionManager.drop])가 접속([FtpSessionManager.connect])이나 진행 중인 목록 조회와 겹쳐도
 * 연결 성공으로 뒤집히지 않고 명령을 연결 끊김으로 끝내는지, 그리고 LIST 서버의 목록 조회 명령 형태를 검증하는 통합 테스트.
 * 소켓을 닫을 때의 예외와 명령 인자는 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpSessionManagerCloseTest {

    /** 검증 대상을 실제 클라이언트로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture()
    /** 검증 대상. */
    private val manager = fixture.manager
    /** 탐색 세션이 쓰는 클라이언트. 명령을 보내기 직전에 테스트가 끼어들 수 있다. */
    private val client = InterceptingFtpClient().also { intercepting -> fixture.clientFactory = { intercepting } }
    /** 서버가 받은 LIST 인자. 받은 순서대로 쌓는다. */
    private val listArguments = CopyOnWriteArrayList<String>()
    /** 서버가 받은 CWD 인자. 받은 순서대로 쌓는다. */
    private val cwdArguments = CopyOnWriteArrayList<String>()
    /** 마지막으로 띄운 서버. */
    private lateinit var server: ScriptedFtpServer

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `LIST 서버에 연결할 때는 목록을 조회하지 않는다`() {
        val result = connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(ConnectResult.Connected, result)
        assertFalse(server.received.toString(), "LIST" in server.received)
    }

    @Test
    fun `LIST 서버의 목록은 디렉터리로 이동한 뒤 인자 없는 LIST로 조회한다`() {
        connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals(listOf("/r"), cwdArguments)
        assertEquals("경로 인자도 -a 같은 옵션도 보내지 않는다", listOf(""), listArguments)
    }

    @Test
    fun `로그인 직후 종료 요청으로 연결이 닫히면 연결 성공으로 알리지 않는다`() {
        val closer = AtomicReference<Thread?>()
        client.beforeCommand = { command ->
            // 홈을 확인하는 PWD를 보내기 직전에 종료를 요청해, 로그인까지 마친 연결 작업과 종료 요청이 겹치게 한다
            if (command == "PWD") {
                closer.set(thread { runBlocking { manager.disconnect() } })
                awaitUntil("제어 연결 소켓이 닫히지") { client.isControlSocketClosed }
            }
        }
        val result = connectToListServer { exchange -> sendListing(exchange, "a.txt") }
        closer.get()?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertNull(manager.current())
        assertEquals(RemoteConnectionState.Disconnected, manager.connectionState.value)
        assertFalse(client.isConnected)
    }

    @Test
    fun `서버에 접속하기 전에 종료를 요청해도 연결 성공으로 알리지 않는다`() {
        val closer = AtomicReference<Thread?>()
        client.beforeConnect = {
            // 소켓을 열기 전, 연결 작업이 세션 잠금을 쥔 채로 앱 종료가 연결 종료를 요청하게 한다 — 닫을 소켓이 아직 없어도 요청은 남아야 한다.
            // 종료 요청은 세션 잠금을 기다리며 멈춰야 하므로, 그 스레드가 멈출 때까지 기다린다(종료 기록은 잠금을 기다리기 전에 끝난다)
            val disconnecting = thread { runBlocking { manager.disconnect() } }
            closer.set(disconnecting)
            awaitUntil("종료 요청이 세션 잠금을 기다리지") { disconnecting.state == Thread.State.WAITING || disconnecting.state == Thread.State.TIMED_WAITING }
        }
        val result = connectToListServer { exchange -> sendListing(exchange, "a.txt") }
        closer.get()?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertNull(manager.current())
        assertFalse(client.isConnected)
    }

    @Test
    fun `목록을 받는 중 종료를 요청하면 데이터 연결까지 리셋해 명령을 연결 끊김으로 끝낸다`() {
        val dataResetByClient = AtomicBoolean(false)
        val closer = AtomicReference<Thread?>()
        connectToListServer { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Here comes the directory listing")
                socket.getOutputStream().write(listingLines("a.txt").toByteArray())
                closer.set(thread { runBlocking { manager.disconnect() } })
                // 전송을 끝내지 않고 클라이언트가 데이터 연결을 닫을 때까지 기다린다. 중단이므로 정상 닫기(-1)가 아니라 리셋이어야 한다
                socket.soTimeout = ScriptedFtpServer.TIMEOUT_MILLIS
                dataResetByClient.set(isResetOnRead(socket))
            }
        }

        val listing = runBlocking { manager.withBrowsingSession { it.listing.listFiles("ftp://host/r") } }
        closer.get()?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertTrue(listing.toString(), listing is IoResult.ConnectionLost)
        assertTrue("데이터 연결을 리셋해 전송을 기다리지 않는다", dataResetByClient.get())
        assertFalse("진행 중인 명령이 있으면 다른 스레드에서 QUIT을 보내지 않는다", "QUIT" in server.received)
        assertFalse(client.isConnected)
    }

    @Test
    fun `진행 중인 명령이 없을 때 종료를 요청하면 로그아웃한 뒤 연결을 닫는다`() {
        connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        runBlocking { manager.disconnect() }

        assertEquals("QUIT", server.received.last())
        assertNull(manager.current())
        assertEquals(RemoteConnectionState.Disconnected, manager.connectionState.value)
        assertFalse(client.isConnected)
    }

    @Test
    fun `앱이 연결을 더 유지할 수 없어 끝내면 연결을 닫고 끊김으로 남긴다`() {
        connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        assertTrue(runBlocking { manager.drop() })

        assertEquals(RemoteConnectionState.Lost, manager.connectionState.value)
        assertEquals("QUIT", server.received.last())
        assertFalse(client.isConnected)
    }

    @Test
    fun `연결 작업 중에는 끊김으로 끝내지 않아 그 연결 작업이 연결에 성공한다`() {
        val dropped = AtomicReference<Boolean?>()
        client.beforeConnect = {
            // 소켓을 열기 전에 실행 시간 한도의 종료가 들어오게 한다 — 보호하던 연결이 아닌 새 연결이다
            dropped.set(runBlocking { manager.drop() })
        }

        val result = connectToListServer { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(false, dropped.get())
        assertEquals(ConnectResult.Connected, result)
        assertEquals(RemoteConnectionState.Connected("server"), manager.connectionState.value)
    }

    @Test
    fun `연결된 세션이 없으면 끊김으로 끝내지 않는다`() {
        assertFalse(runBlocking { manager.drop() })

        assertEquals(RemoteConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test
    fun `접속과 종료가 모두 끝나면 작업 중으로 보이지 않는다`() {
        connectToListServer { exchange -> sendListing(exchange, "a.txt") }
        assertFalse(manager.isConnecting.value)

        runBlocking { manager.disconnect() }

        assertFalse(manager.isConnecting.value)
    }

    /**
     * LIST에 [onList]대로 응답하는 LIST 서버(MLSD 미지원)를 띄워 접속한다. 받은 LIST·CWD 인자는 [listArguments]·[cwdArguments]에 쌓는다.
     * @param onList LIST를 받았을 때의 응답 동작
     * @return 연결 결과
     */
    private fun connectToListServer(onList: (ScriptedFtpServer.Exchange) -> Unit): ConnectResult {
        server = fixture.server(
            mapOf(
                "CWD" to { exchange ->
                    cwdArguments += exchange.argument
                    exchange.send("250 Directory changed")
                },
                "LIST" to { exchange ->
                    listArguments += exchange.argument
                    onList(exchange)
                },
            )
        )
        return fixture.connect(server)
    }

    /**
     * 탐색 세션으로 [path]의 화면 목록을 조회해 항목 이름을 돌려준다.
     * @param path ftp://server/path 형식의 디렉터리 경로
     * @return 항목 이름 집합
     */
    private fun listedNamesOf(path: String): Set<String> {
        val listing = runBlocking { manager.withBrowsingSession { it.listing.listFiles(path) } }
        return (listing as IoResult.Success).value.mapTo(HashSet()) { (it as ListedItem.Entry).entry.name }
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
     * 서버가 받은 데이터 연결을 읽어, 클라이언트가 그 연결을 리셋으로 끝냈는지 판별한다.
     * @param socket 서버 쪽 데이터 소켓
     * @return 리셋이면 true, 정상 닫기(파일 끝)거나 데이터가 왔으면 false
     */
    private fun isResetOnRead(socket: Socket): Boolean = try {
        socket.getInputStream().read()
        false
    } catch (_: SocketException) {
        true
    }

    /** 명령을 보내기 직전에 테스트가 끼어들 수 있는 실제 FTP 클라이언트. 연결 단계 사이에 종료 요청을 끼워 넣는 데 쓴다. */
    private class InterceptingFtpClient : SessionFtpClient() {
        /** 명령을 보내기 직전에 실행할 동작. 보낼 명령 이름을 받는다. 명령을 보내는 스레드에서 실행된다. */
        @Volatile var beforeCommand: (String) -> Unit = {}
        /** 서버에 접속(소켓 열기)하기 직전에 실행할 동작. 연결 작업의 스레드에서 실행된다. */
        @Volatile var beforeConnect: () -> Unit = {}
        /** 제어 연결 소켓이 닫혔는지(소켓이 없으면 닫힌 것으로 본다). */
        val isControlSocketClosed: Boolean get() = _socket_?.isClosed ?: true

        override fun sendCommand(command: String, args: String?): Int {
            beforeCommand(command)
            return super.sendCommand(command, args)
        }

        override fun connect(hostname: String, port: Int) {
            beforeConnect()
            super.connect(hostname, port)
        }
    }
}
