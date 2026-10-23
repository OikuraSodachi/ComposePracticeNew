package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingCompleteness
import com.todokanai.composepracticenew.repository.FetchedListing
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.io.Writer
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * 실제 commons-net FTPClient로 LIST의 450을 데이터 전송 전·후로 구분하는지 검증하는 통합 테스트.
 * 가짜 클라이언트는 응답 이벤트를 직접 발생시키므로, "commons-net이 LIST의 1xx 응답을 수신기에 알린다"는 전제는 이 테스트만 확인한다.
 * 로컬 소켓으로 정해진 응답을 보내는 서버를 띄우고 수동(PASV) 모드로 조회한다.
 */
class FtpListingRealClientTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = FTPClient().apply { defaultTimeout = TIMEOUT_MILLIS }
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runCatching { client.disconnect() }
        server?.close()
    }

    @Test
    fun `데이터 연결을 열기 전에 받은 450은 확인되지 않은 빈 목록이다`() {
        val result = listWith { data, send ->
            data.accept().close()
            send("450 No files found")
        }

        assertEquals(IoResult.Success(FetchedListing(emptyList(), ListingCompleteness.UNCONFIRMED)), result)
    }

    @Test
    fun `150으로 목록을 보내기 시작한 뒤 받은 450은 받은 항목이 있어도 실패다`() {
        val result = listWith { data, send ->
            data.accept().use { socket ->
                send("150 Here comes the directory listing")
                socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
            }
            send("450 Transfer aborted")
        }

        assertEquals(IoResult.ItemFailed("LIST 실패: 450 Transfer aborted"), result)
    }

    @Test
    fun `150으로 목록을 보내고 226으로 끝나면 받은 항목이 확인된 목록이다`() {
        val result = listWith { data, send ->
            data.accept().use { socket ->
                send("150 Here comes the directory listing")
                socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
            }
            send("226 Transfer complete")
        }

        assertEquals(listOf("ftp://host/r/a.txt"), ((result as IoResult.Success).value).entries.map { it.path })
        assertEquals(ListingCompleteness.COMPLETE, result.value.completeness)
    }

    /**
     * [onList]대로 LIST에 응답하는 서버에 실제 클라이언트로 접속해 화면 목록을 조회한다.
     * @param onList LIST를 받았을 때 데이터 소켓과 제어 응답 함수로 응답하는 동작
     * @return 조회 결과
     */
    private fun listWith(onList: (data: ServerSocket, send: (String) -> Unit) -> Unit): IoResult<FetchedListing> {
        val scripted = ScriptedFtpServer(onList).also { server = it }
        client.connect(InetAddress.getLoopbackAddress(), scripted.port)
        client.soTimeout = TIMEOUT_MILLIS
        client.enterLocalPassiveMode()
        val session = FtpSession(client).also { runBlocking { it.markLoggedIn() } }
        return runBlocking { FtpCommandExecutor(session).listFiles("ftp://host/r") }
    }

    /**
     * 제어 연결 하나를 받아 정해진 대로 응답하는 테스트용 FTP 서버. SYST·PASV에는 고정 응답을, LIST에는 [onList]를 실행하고 나머지는 502로 거부한다.
     * @param onList LIST를 받았을 때의 응답 동작
     */
    private class ScriptedFtpServer(private val onList: (data: ServerSocket, send: (String) -> Unit) -> Unit) : Closeable {
        /** 제어 연결을 받을 소켓. */
        private val control = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        /** PASV로 알린 데이터 연결을 받을 소켓. */
        private val data = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        /** 제어 연결 포트. */
        val port: Int get() = control.localPort
        /** 제어 연결을 처리하는 스레드. */
        private val worker = thread(isDaemon = true) { runCatching { serve() } }

        /** 제어 연결 하나를 받아 명령마다 응답한다. */
        private fun serve() {
            control.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                val send = { line: String -> writer.sendLine(line) }
                send("220 ready")
                while (true) {
                    val command = reader.readLine()?.substringBefore(' ')?.uppercase() ?: return
                    when (command) {
                        "SYST" -> send("215 UNIX Type: L8")
                        "PASV" -> send("227 Entering Passive Mode (127,0,0,1,${data.localPort / 256},${data.localPort % 256})")
                        "LIST" -> onList(data, send)
                        "QUIT" -> return send("221 Bye")
                        else -> send("502 Command not implemented")
                    }
                }
            }
        }

        /** 응답 한 줄을 CRLF로 끝맺어 보낸다. */
        private fun Writer.sendLine(line: String) {
            write("$line\r\n")
            flush()
        }

        override fun close() {
            control.close()
            data.close()
            worker.join(TIMEOUT_MILLIS.toLong())
        }
    }

    private companion object {
        /** 연결·응답 대기 제한 시간(ms). 서버가 응답하지 않아도 테스트가 멈추지 않게 한다. */
        const val TIMEOUT_MILLIS = 5_000
    }
}
