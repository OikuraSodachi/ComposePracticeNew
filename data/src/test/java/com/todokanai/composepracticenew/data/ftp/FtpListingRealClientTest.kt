package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer.Companion.TIMEOUT_MILLIS
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingCompleteness
import com.todokanai.composepracticenew.repository.FetchedListing
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

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
        val result = listWith { exchange ->
            exchange.data.accept().close()
            exchange.send("450 No files found")
        }

        assertEquals(IoResult.Success(FetchedListing(emptyList(), ListingCompleteness.UNCONFIRMED)), result)
    }

    @Test
    fun `150으로 목록을 보내기 시작한 뒤 받은 450은 받은 항목이 있어도 실패다`() {
        val result = listWith { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Here comes the directory listing")
                socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
            }
            exchange.send("450 Transfer aborted")
        }

        assertEquals(IoResult.ItemFailed("LIST 실패: 450 Transfer aborted"), result)
    }

    @Test
    fun `150으로 목록을 보내고 226으로 끝나면 받은 항목이 확인된 목록이다`() {
        val result = listWith { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Here comes the directory listing")
                socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
            }
            exchange.send("226 Transfer complete")
        }

        assertEquals(listOf("ftp://host/r/a.txt"), ((result as IoResult.Success).value).entries.map { it.path })
        assertEquals(ListingCompleteness.COMPLETE, result.value.completeness)
    }

    /**
     * [onList]대로 LIST에 응답하는 서버에 실제 클라이언트로 접속해 화면 목록을 조회한다.
     * @param onList LIST를 받았을 때의 응답 동작
     * @return 조회 결과
     */
    private fun listWith(onList: (ScriptedFtpServer.Exchange) -> Unit): IoResult<FetchedListing> {
        val scripted = ScriptedFtpServer(mapOf("LIST" to onList)).also { server = it }
        client.connect(scripted.host, scripted.port)
        client.soTimeout = TIMEOUT_MILLIS
        client.enterLocalPassiveMode()
        val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null) } }
        return runBlocking { FtpCommandExecutor(session).listFiles("ftp://host/r") }
    }
}
