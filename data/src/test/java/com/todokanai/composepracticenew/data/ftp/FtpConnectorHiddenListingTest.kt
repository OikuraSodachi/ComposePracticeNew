package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 연결할 때([FtpConnector.connect]) LIST 서버가 `-a` 옵션을 숨김 항목을 더하라는 뜻으로 받아들일 때만 이후 LIST에 붙이는지 검증하는 통합 테스트.
 * 옵션이 명령 인자로 어떻게 나가는지는 commons-net 내부 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpConnectorHiddenListingTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = FTPClient().apply { defaultTimeout = ScriptedFtpServer.TIMEOUT_MILLIS }
    /** 연결 상태를 소유한 세션. */
    private val session = FtpSession(client)
    /** 연결에 성공하면 시작되는 Keep-Alive. 테스트가 끝나면 멈춘다. */
    private val keepAlive = FtpKeepAlive(session)
    /** 검증 대상. */
    private val connector = FtpConnector(session, keepAlive)
    /** 서버가 받은 LIST 인자. 받은 순서대로 쌓는다. */
    private val listArguments = CopyOnWriteArrayList<String>()
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runBlocking { keepAlive.stop() }
        runCatching { client.disconnect() }
        server?.close()
    }

    @Test
    fun `서버가 -a에 숨김 항목만 더해 보내면 이후 목록 조회에 -a를 붙여 숨김 항목도 받는다`() {
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) sendListing(exchange, ".", "..", ".hidden", "a.txt") else sendListing(exchange, "a.txt")
        }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(setOf(".hidden", "a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("-a /r", listArguments.last())
    }

    @Test
    fun `홈 디렉터리가 숨김 항목만 담고 있어도 -a 목록에 숨김 항목이 더해지면 -a를 붙인다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange, ".", "..", ".profile") else sendListing(exchange) }

        listedNamesOf("ftp://host/r")

        assertEquals("-a /r", listArguments.last())
    }

    @Test
    fun `서버가 -a를 무시하고 같은 목록을 보내면 -a를 붙여도 목록은 같다`() {
        connectTo { exchange -> sendListing(exchange, "a.txt") }

        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("-a /r", listArguments.last())
    }

    @Test
    fun `서버가 -a를 경로로 읽고 거부하면 -a를 붙이지 않는다`() {
        val result = connectTo { exchange ->
            if (exchange.hasHiddenOption()) refuse(exchange, "550 -a: No such file or directory") else sendListing(exchange, "a.txt")
        }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(setOf("a.txt"), listedNamesOf("ftp://host/r"))
        assertEquals("/r", listArguments.last())
    }

    @Test
    fun `-a 목록에서 옵션 없는 목록의 항목이 빠지면 다른 목록으로 보고 -a를 붙이지 않는다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange) else sendListing(exchange, "a.txt") }

        listedNamesOf("ftp://host/r")

        assertEquals("/r", listArguments.last())
    }

    @Test
    fun `-a 목록에 숨김이 아닌 항목이 더해지면 다른 목록으로 보고 -a를 붙이지 않는다`() {
        connectTo { exchange -> if (exchange.hasHiddenOption()) sendListing(exchange, "a.txt", "b.txt") else sendListing(exchange, "a.txt") }

        listedNamesOf("ftp://host/r")

        assertEquals("/r", listArguments.last())
    }

    @Test
    fun `홈 디렉터리와 -a 목록이 모두 비면 판정할 근거가 없어 -a를 붙이지 않는다`() {
        connectTo { exchange -> sendListing(exchange) }

        listedNamesOf("ftp://host/r")

        assertEquals("/r", listArguments.last())
    }

    @Test
    fun `홈 디렉터리 목록을 받지 못해도 연결하고 -a는 보내지 않는다`() {
        val result = connectTo { exchange -> refuse(exchange, "550 Permission denied") }

        assertEquals(ConnectResult.Connected, result)
        assertTrue(runBlocking { session.isReady() })
        assertFalse(listArguments.toString(), listArguments.any { it.startsWith("-a") })
    }

    @Test
    fun `MLSD를 지원하는 서버에는 -a를 판정하려고 LIST를 보내지 않는다`() {
        val scripted = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "FEAT" to {
                it.send("211-Features:")
                it.send(" MLST type*;size*;modify*;")
                it.send("211 End")
            },
            "PWD" to { it.send("257 \"/home\" is the current directory") },
        )
        val result = ScriptedFtpServer(scripted).also { server = it }
            .let { runBlocking { connector.connect(it.host, it.port, "user", "password", "UTF-8") } }

        assertEquals(ConnectResult.Connected, result)
        assertFalse(server!!.received.toString(), "LIST" in server!!.received)
        assertFalse(client.listHiddenFiles)
    }

    /**
     * LIST에 [onList]대로 응답하고 홈 디렉터리가 /home인 서버를 띄워 [FtpConnector]로 접속한다. 받은 LIST 인자는 [listArguments]에 쌓는다.
     * @param onList LIST를 받았을 때의 응답 동작
     * @return 연결 결과
     */
    private fun connectTo(onList: (ScriptedFtpServer.Exchange) -> Unit): ConnectResult {
        val scripted = ScriptedFtpServer(
            mapOf(
                "PWD" to { it.send("257 \"/home\" is the current directory") },
                "LIST" to { exchange ->
                    listArguments += exchange.argument
                    onList(exchange)
                },
            )
        ).also { server = it }
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

    /** 이 LIST가 `-a` 옵션을 붙였는지 판별한다. */
    private fun ScriptedFtpServer.Exchange.hasHiddenOption(): Boolean = argument.startsWith("-a")

    /**
     * 데이터 연결로 [names]를 유닉스 형식 목록으로 보내고 226으로 끝낸다. "."·".."은 디렉터리, 나머지는 1바이트 파일로 보낸다.
     * @param exchange 응답 중인 서버 쪽 연결
     * @param names 보낼 항목 이름
     */
    private fun sendListing(exchange: ScriptedFtpServer.Exchange, vararg names: String) {
        exchange.data.accept().use { socket ->
            exchange.send("150 Here comes the directory listing")
            val lines = names.joinToString("") { name ->
                if (name == "." || name == "..") "drwxr-xr-x   2 user group     4096 Jan 01 00:00 $name\r\n"
                else "-rw-r--r--   1 user group        1 Jan 01 00:00 $name\r\n"
            }
            socket.getOutputStream().write(lines.toByteArray())
        }
        exchange.send("226 Transfer complete")
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
}
