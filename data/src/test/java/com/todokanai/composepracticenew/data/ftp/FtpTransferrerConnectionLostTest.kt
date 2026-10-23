package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 전송 완료 확인이나 크기 조회 중 연결이 끊기면(EOF·421) [FtpTransferrer]가 이를 일반 실패가 아닌 연결 끊김으로 알리고,
 * 세션을 끊김 상태로 바꾸며, 폴더 전송의 남은 파일을 시도하지 않는지 검증하는 단위 테스트.
 */
class FtpTransferrerConnectionLostTest {

    private val dir: File = Files.createTempDirectory("ftp-connection-lost-test").toFile()
    private val client = FakeFtpClient().apply { streamContent = "data".toByteArray() }
    private val session = FtpSession(client)
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `다운로드 완료 확인 중 연결이 끊기면 연결 끊김으로 알리고 세션을 끊김 상태로 바꾼다`() {
        loggedIn()
        client.onCompletePending = { throw FTPConnectionClosedException("Connection closed without indication.") }

        val result = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `업로드 완료 확인 중 서버가 421로 닫으면 연결 끊김으로 알린다`() {
        loggedIn()
        client.onCompletePending = { throw FTPConnectionClosedException("FTP response 421 received.  Server closed connection.") }
        val local = File(dir, "a.txt").apply { writeText("A") }

        val result = runBlocking { transferrer.upload(local.path, "ftp://host/r/a.txt") }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `폴더 다운로드 중 완료 확인에서 연결이 끊기면 남은 파일을 시도하지 않고 중단한다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt", "b.txt")
        client.onCompletePending = { throw FTPConnectionClosedException("Connection closed without indication.") }

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true) }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertEquals(listOf("/r/a.txt"), client.retrieveRequests)
    }

    @Test
    fun `크기 조회 중 연결이 끊기면 크기 미확인으로 넘기지 않고 연결 끊김으로 알린다`() {
        loggedIn()
        client.onMlst = { throw FTPConnectionClosedException("Connection closed without indication.") }

        val result = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertTrue("전송을 시작하지 않아야 함", client.retrieveRequests.isEmpty())
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `완료 확인이 연결 끊김 없이 실패하면 그 항목만 실패로 알리고 세션을 유지한다`() {
        loggedIn()
        client.onCompletePending = { false }

        val result = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }

        assertTrue("$result", (result as IoResult.ItemFailed).message.orEmpty().startsWith("전송 미완료"))
        assertTrue(runBlocking { session.isReady() })
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark()) }
}
