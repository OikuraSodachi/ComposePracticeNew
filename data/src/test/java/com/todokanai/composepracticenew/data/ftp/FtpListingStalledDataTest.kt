package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer.Companion.TIMEOUT_MILLIS
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 목록 조회 중 서버가 목록 데이터를 보내지 않으면 [FtpCommandExecutor]가 데이터 연결의 읽기 상한에 그 폴더만 실패로 끝내고,
 * 완료 응답까지 읽어 세션을 유지하는지 검증하는 통합 테스트(#282 리뷰). 데이터 연결은 정상 닫기가 아니라 리셋으로 끝내는지도 검증한다.
 * 실제 commons-net FTPClient로 로컬 소켓의 정해진 응답 서버에 수동(PASV) 모드로 접속한다.
 */
class FtpListingStalledDataTest {

    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = ListingStreamFtpClient()
    /** 검증 대상 세션. 데이터 연결의 읽기 상한을 짧게 둔다. */
    private val session = FtpSession(client, ioTimeoutMillis = IO_TIMEOUT_MILLIS)
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runCatching { client.disconnect() }
        server?.close()
    }

    // 상한이 동작하지 않으면 조회가 끝나지 않으므로 테스트 시간을 제한한다
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `목록 데이터가 오지 않으면 그 폴더만 실패하고 세션은 다음 조회를 그대로 처리한다`() {
        val listCount = AtomicInteger()
        val serverEnd = AtomicReference<DataEnd>()
        connect { exchange ->
            // 첫 조회만 목록을 보내지 않는다
            val stall = listCount.getAndIncrement() == 0
            exchange.data.accept().use { socket ->
                exchange.send("150 Here comes the directory listing")
                // 멈춘 조회는 클라이언트가 데이터 연결을 닫을 때까지 기다린다
                if (stall) serverEnd.set(readToEnd(socket))
                else socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
            }
            exchange.send(if (stall) "426 Transfer aborted" else "226 Transfer complete")
        }
        val commands = FtpCommandExecutor(session)

        val stalled = runBlocking { commands.listFiles("ftp://host/r") }
        val next = runBlocking { commands.listFiles("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("LIST 실패: Read timed out"), stalled)
        assertEquals(DataEnd.RESET, serverEnd.get())
        assertTrue(runBlocking { session.isReady() })
        assertEquals(listOf("ftp://host/r/a.txt"), (next as IoResult.Success).value.map { it.path })
    }

    /**
     * 받는 쪽 데이터 소켓을 끝까지 읽어, 상대가 연결을 어떻게 끝냈는지 돌려준다.
     * @param socket 서버가 받은 데이터 연결
     * @return 정상 닫기면 [DataEnd.EOF], 리셋이면 [DataEnd.RESET]
     */
    private fun readToEnd(socket: Socket): DataEnd = try {
        val input = socket.getInputStream()
        while (input.read() != -1) Unit
        DataEnd.EOF
    } catch (_: SocketException) {
        DataEnd.RESET
    }

    /** 클라이언트가 데이터 연결을 끝낸 방식. */
    private enum class DataEnd {
        /** 정상 닫기. */
        EOF,
        /** 연결 리셋. 중단으로 읽힌다. */
        RESET
    }

    /**
     * [onList]대로 LIST에 응답하는 서버에 접속해 세션을 로그인 상태로 둔다.
     * 제어 연결의 응답 대기는 서버가 늦게 보내는 완료 응답을 받을 만큼 길게 다시 둔다 — 이 테스트는 데이터 연결의 상한만 검증한다.
     * @param onList LIST를 받았을 때의 응답 동작
     */
    private fun connect(onList: (ScriptedFtpServer.Exchange) -> Unit) {
        val scripted = ScriptedFtpServer(mapOf("LIST" to onList)).also { server = it }
        client.connect(scripted.host, scripted.port)
        client.soTimeout = TIMEOUT_MILLIS
        client.enterLocalPassiveMode()
        runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
    }

    private companion object {
        /** 세션의 소켓 읽기·쓰기 상한(ms). */
        const val IO_TIMEOUT_MILLIS = 300
        /** 조회가 끝나지 않을 때 테스트를 실패로 끝내는 상한(ms). */
        const val TEST_TIMEOUT_MILLIS = 15_000L
    }
}
