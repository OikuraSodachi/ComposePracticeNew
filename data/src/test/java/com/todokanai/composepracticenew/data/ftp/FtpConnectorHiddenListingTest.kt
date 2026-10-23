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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 연결할 때([FtpConnector.connect]) LIST 서버가 `-a` 옵션을 숨김 항목을 더하라는 뜻으로 받아들일 때만 이후 LIST에 붙이고,
 * 그 판정이 연결을 끊어도 다시 연결해 옵션 없이 쓰는지 검증하는 통합 테스트.
 * 옵션이 명령 인자로 어떻게 나가는지와 연결이 끊길 때의 예외는 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpConnectorHiddenListingTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. 데이터 연결을 열기 직전에 테스트가 끼어들 수 있다. */
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
    fun `서버가 -a에 숨김 항목만 더해 보내면 이후 목록 조회에 -a를 붙여 숨김 항목도 받는다`() {
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) sendListing(exchange, ".", "..", ".hidden", "a.txt") else sendListing(exchange, "a.txt")
        }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(setOf(".hidden", "a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("/r", cwdArguments.last())
        assertEquals("목록은 디렉터리로 이동한 뒤 현재 디렉터리를 조회한다", "-a .", listArguments.last())
    }

    @Test
    fun `판정은 이후 조회와 같은 형태로 현재 디렉터리를 조회한다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange, ".hidden", "a.txt") else sendListing(exchange, "a.txt") }

        // -a에는 현재 디렉터리를 덧붙인다 — 인자가 하나면 그 이름의 항목이 있을 때 경로로 읽는 서버(ProFTPD)가 있다
        assertEquals(listOf("", "-a ."), listArguments)
        assertEquals("판정은 디렉터리를 옮기지 않는다", emptyList<String>(), cwdArguments)
    }

    @Test
    fun `현재 디렉터리가 숨김 항목만 담고 있어도 -a 목록에 숨김 항목이 더해지면 -a를 붙인다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange, ".", "..", ".profile") else sendListing(exchange) }

        listedNamesOf("ftp://host/r")

        assertEquals("-a .", listArguments.last())
    }

    @Test
    fun `서버가 -a를 무시하고 같은 목록을 보내면 -a를 붙여도 목록은 같다`() {
        connectTo { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("-a .", listArguments.last())
    }

    @Test
    fun `-a 판정 조회의 데이터 연결을 열지 못하면 제어 연결은 멀쩡하므로 다시 연결하지 않고 -a 없이 쓴다`() {
        var passiveCount = 0
        val result = connectTo(
            onPasv = { exchange ->
                // 두 번째 PASV(-a 판정 조회)만 닫힌 포트를 알려 데이터 연결을 열지 못하게 한다
                val port = if (++passiveCount == 2) closedPort() else exchange.data.localPort
                exchange.send("227 Entering Passive Mode (127,0,0,1,${port / 256},${port % 256})")
            }
        ) { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(ConnectResult.Connected, result)
        assertEquals("다시 로그인하지 않는다", 1, servers.last().received.count { it == "USER" })
        assertFalse(client.listHiddenFiles)
        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("", listArguments.last())
    }

    @Test
    fun `판정 중 앱이 연결 종료를 요청해 연결이 끊기면 다시 연결하지 않고 실패로 알린다`() {
        val mark = session.closeRequestMark()
        var closer: Thread? = null
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) {
                closer = thread { runBlocking { session.closeBypassingIoLock() } }
                while (!session.isCloseRequestedSince(mark)) Thread.yield()
                refuse(exchange, "421 Service not available, closing control connection")
                exchange.hangUp()
            } else {
                sendListing(exchange, "a.txt")
            }
        }
        closer?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertEquals("종료 요청을 다시 연결로 뒤집지 않는다", 1, servers.last().received.count { it == "USER" })
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `판정 중 종료 요청으로 닫힌 연결에 다음 명령을 보내지 못해 판정이 끝나도 연결 성공으로 알리지 않는다`() {
        var closer: Thread? = null
        client.beforeDataConnection = { index ->
            // 두 번째 데이터 연결(-a 판정 조회) 직전에 종료를 요청해, PASV를 보내지 못한 실패가 응답을 기다리지 않는 실패로 끝나게 한다
            if (index == 1) {
                closer = thread { runBlocking { session.closeBypassingIoLock() } }
                while (!client.isControlSocketClosed) Thread.yield()
            }
        }
        val result = connectTo { exchange -> sendListing(exchange, "a.txt") }
        closer?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertEquals("종료 요청을 다시 연결로 뒤집지 않는다", 1, servers.last().received.count { it == "USER" })
        assertFalse(runBlocking { session.isReady() })
        assertFalse(client.isConnected)
    }

    @Test
    fun `목록을 받는 중 종료를 요청하면 데이터 연결까지 닫아 명령을 연결 끊김으로 끝낸다`() {
        val connected = AtomicBoolean(false)
        val dataClosedByClient = AtomicBoolean(false)
        var closer: Thread? = null
        connectTo { exchange ->
            if (!connected.get()) return@connectTo sendListing(exchange, "a.txt")
            exchange.data.accept().use { socket ->
                exchange.send("150 Here comes the directory listing")
                socket.getOutputStream().write(listingLines("a.txt").toByteArray())
                closer = thread { runBlocking { session.closeBypassingIoLock() } }
                // 전송을 끝내지 않고 클라이언트가 데이터 연결을 닫을 때까지 기다린다
                socket.soTimeout = ScriptedFtpServer.TIMEOUT_MILLIS
                dataClosedByClient.set(socket.getInputStream().read() == -1)
            }
        }
        connected.set(true)

        val listing = runBlocking { FtpCommandExecutor(session).listFiles("ftp://host/r") }
        closer?.join(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())

        assertTrue(listing.toString(), listing is IoResult.ConnectionLost)
        assertTrue("데이터 연결을 닫아 전송을 기다리지 않는다", dataClosedByClient.get())
        assertFalse("진행 중인 명령이 있으면 다른 스레드에서 QUIT을 보내지 않는다", "QUIT" in servers.last().received)
        assertFalse(client.isConnected)
    }

    @Test
    fun `진행 중인 명령이 없을 때 종료를 요청하면 로그아웃한 뒤 연결을 닫는다`() {
        connectTo { exchange -> sendListing(exchange, "a.txt") }

        runBlocking { session.closeBypassingIoLock() }

        assertEquals("QUIT", servers.last().received.last())
        assertFalse(runBlocking { session.isReady() })
        assertFalse(client.isConnected)
    }

    @Test
    fun `서버가 -a를 경로로 읽고 거부하면 -a를 붙이지 않는다`() {
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) refuse(exchange, "550 -a: No such file or directory") else sendListing(exchange, "a.txt")
        }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("", listArguments.last())
    }

    @Test
    fun `-a 목록에서 옵션 없는 목록의 항목이 빠지면 다른 목록으로 보고 -a를 붙이지 않는다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange) else sendListing(exchange, "a.txt") }

        listedNamesOf("ftp://host/r")

        assertEquals("", listArguments.last())
    }

    @Test
    fun `-a 목록에 숨김이 아닌 항목이 더해지면 다른 목록으로 보고 -a를 붙이지 않는다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange, "a.txt", "b.txt") else sendListing(exchange, "a.txt") }

        listedNamesOf("ftp://host/r")

        assertEquals("", listArguments.last())
    }

    @Test
    fun `현재 디렉터리와 -a 목록이 모두 비면 판정할 근거가 없어 -a를 붙이지 않는다`() {
        connectTo { exchange -> sendListing(exchange) }

        listedNamesOf("ftp://host/r")

        assertEquals("", listArguments.last())
    }

    @Test
    fun `현재 디렉터리 목록을 받지 못해도 연결하고 -a는 보내지 않는다`() {
        val result = connectTo { exchange -> refuse(exchange, "550 Permission denied") }

        assertEquals(ConnectResult.Connected, result)
        assertTrue(runBlocking { session.isReady() })
        assertFalse(listArguments.toString(), listArguments.any { it.startsWith("-a") })
    }

    @Test
    fun `-a 조회에 서버가 421로 연결을 끊으면 다시 연결해 -a 없이 쓴다`() {
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) {
                refuse(exchange, "421 Service not available, closing control connection")
                exchange.hangUp()
            } else {
                sendListing(exchange, "a.txt")
            }
        }

        assertReconnectedWithoutOption(result)
    }

    @Test
    fun `-a 목록을 보내다 서버가 응답 없이 연결을 끊으면 다시 연결해 -a 없이 쓴다`() {
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) {
                exchange.data.accept().use { socket ->
                    exchange.send("150 Here comes the directory listing")
                    socket.getOutputStream().write(listingLines("a.txt").toByteArray())
                }
                exchange.hangUp()
            } else {
                sendListing(exchange, "a.txt")
            }
        }

        assertReconnectedWithoutOption(result)
    }

    @Test
    fun `옵션 없는 판정 조회에서 연결이 끊겨도 다시 연결해 -a 없이 쓴다`() {
        val result = connectTo { exchange ->
            if (exchange.connectionIndex == 0) {
                refuse(exchange, "421 Service not available, closing control connection")
                exchange.hangUp()
            } else {
                sendListing(exchange, "a.txt")
            }
        }

        assertReconnectedWithoutOption(result)
        assertFalse(listArguments.toString(), listArguments.any { it.startsWith("-a") })
    }

    @Test
    fun `판정이 연결을 끊은 뒤 다시 연결하다 로그인이 거부되면 연결 실패로 알린다`() {
        val result = connectTo(
            onPass = { exchange -> exchange.send(if (exchange.connectionIndex == 0) "230 Logged in" else "530 Login incorrect") }
        ) { exchange ->
            refuse(exchange, "421 Service not available, closing control connection")
            exchange.hangUp()
        }

        assertEquals(ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `-a를 쓰던 연결에서 같은 클라이언트로 다른 서버에 연결하면 판정 첫 조회에 -a를 붙이지 않는다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange, ".hidden", "a.txt") else sendListing(exchange, "a.txt") }
        assertTrue("첫 서버에서는 -a를 쓴다", client.listHiddenFiles)
        listArguments.clear()

        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) refuse(exchange, "550 -a: No such file or directory") else sendListing(exchange, "b.txt")
        }

        assertEquals(ConnectResult.Connected, result)
        assertEquals("", listArguments.first())
        assertEquals(setOf("b.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("", listArguments.last())
    }

    @Test
    fun `MLSD를 지원하는 서버에는 -a를 판정하려고 LIST를 보내지 않는다`() {
        val scripted = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "FEAT" to {
                it.send("211-Features:")
                it.send(" MLST type*;size*;modify*;")
                it.send("211 End")
            },
        )
        val server = ScriptedFtpServer(scripted).also { servers += it }
        val result = runBlocking { connector.connect(server.host, server.port, "user", "password", "UTF-8") }

        assertEquals(ConnectResult.Connected, result)
        assertFalse(server.received.toString(), "LIST" in server.received)
        assertFalse(client.listHiddenFiles)
    }

    /**
     * 판정이 연결을 끊은 뒤 다시 연결해 연결에 성공했고, 이후 목록 조회에 `-a`를 붙이지 않는지 확인한다.
     * @param result 연결 결과
     */
    private fun assertReconnectedWithoutOption(result: ConnectResult) {
        assertEquals(ConnectResult.Connected, result)
        assertTrue(runBlocking { session.isReady() })
        assertEquals("판정이 끊은 연결을 버리고 한 번 더 로그인한다", 2, servers.last().received.count { it == "USER" })
        assertFalse(client.listHiddenFiles)
        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("", listArguments.last())
    }

    /**
     * LIST에 [onList]대로 응답하는 서버를 띄워 [FtpConnector]로 접속한다. 받은 LIST·CWD 인자는 [listArguments]·[cwdArguments]에 쌓는다.
     * @param onPass PASS를 받았을 때의 응답 동작
     * @param onPasv PASV를 받았을 때의 응답 동작. null이면 서버의 기본 응답을 쓴다
     * @param onList LIST를 받았을 때의 응답 동작
     * @return 연결 결과
     */
    private fun connectTo(
        onPass: (ScriptedFtpServer.Exchange) -> Unit = { it.send("230 Logged in") },
        onPasv: ((ScriptedFtpServer.Exchange) -> Unit)? = null,
        onList: (ScriptedFtpServer.Exchange) -> Unit
    ): ConnectResult {
        val script = mutableMapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "PASS" to onPass,
            "CWD" to { exchange ->
                cwdArguments += exchange.argument
                exchange.send("250 Directory changed")
            },
            "LIST" to { exchange ->
                listArguments += exchange.argument
                onList(exchange)
            },
        )
        onPasv?.let { script["PASV"] = it }
        val scripted = ScriptedFtpServer(script).also { servers += it }
        return runBlocking { connector.connect(scripted.host, scripted.port, "user", "password", "UTF-8") }
    }

    /**
     * 연결한 세션으로 [path]의 화면 목록을 조회해 항목 이름을 돌려준다.
     * @param path ftp://server/path 형식의 디렉터리 경로
     * @return 항목 이름 집합
     */
    private fun listedNamesOf(path: String): Set<String> {
        val listing = runBlocking { FtpCommandExecutor(session).listFiles(path) }
        return (listing as IoResult.Success).value.entries.mapTo(HashSet()) { it.name }
    }

    /**
     * 열었다 닫아 아무도 받지 않는 로컬 포트를 구한다. 그 포트로 데이터 연결을 열면 거부된다.
     * @return 닫힌 포트 번호
     */
    private fun closedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    /** 이 LIST가 `-a` 옵션을 붙였는지 판별한다. */
    private fun ScriptedFtpServer.Exchange.hasHiddenOption(): Boolean = argument.startsWith("-a")

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
     * [names]를 유닉스 형식 목록 줄로 만든다. "."·".."은 디렉터리, 나머지는 1바이트 파일이다.
     * @param names 항목 이름
     * @return CRLF로 끝나는 목록 줄을 이은 문자열
     */
    private fun listingLines(vararg names: String): String = names.joinToString("") { name ->
        if (name == "." || name == "..") "drwxr-xr-x   2 user group     4096 Jan 01 00:00 $name\r\n"
        else "-rw-r--r--   1 user group        1 Jan 01 00:00 $name\r\n"
    }

    /**
     * 클라이언트가 연 데이터 연결을 받아 닫고 [reply]로 거부한다. 받지 않으면 다음 조회가 남은 연결을 받는다.
     * @param exchange 응답 중인 서버 쪽 연결
     * @param reply 거부 응답 줄
     */
    private fun refuse(exchange: ScriptedFtpServer.Exchange, reply: String) {
        exchange.data.accept().close()
        exchange.send(reply)
    }

    /** 데이터 연결을 열기 직전에 테스트가 끼어들 수 있는 실제 FTP 클라이언트. 판정 단계 사이에 종료 요청을 끼워 넣는 데 쓴다. */
    private class InterceptingFtpClient : FTPClient() {
        /** 데이터 연결을 열기 직전에 실행할 동작. 이 클라이언트가 연 데이터 연결의 순번(0부터)을 받는다. 연결 작업 스레드에서 실행된다. */
        @Volatile var beforeDataConnection: (Int) -> Unit = {}
        /** 지금까지 열려고 한 데이터 연결 수. */
        private var dataConnections = 0
        /** 제어 연결 소켓이 닫혔는지(소켓이 없으면 닫힌 것으로 본다). */
        val isControlSocketClosed: Boolean get() = _socket_?.isClosed ?: true

        override fun _openDataConnection_(command: String?, arg: String?): Socket? {
            beforeDataConnection(dataConnections++)
            return super._openDataConnection_(command, arg)
        }
    }
}
