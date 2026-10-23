package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.file.Files

/**
 * 제어 채널의 응답을 읽다 실패(타임아웃 등)하면 응답 순서를 알 수 없으므로 세션을 종료하고 연결 끊김으로 알리며,
 * 서버 오류 응답이나 데이터 스트림 오류처럼 응답 순서가 유지된 실패는 그 항목만 실패로 처리하는지 검증하는 단위 테스트.
 * 명령 하나의 응답 단계별 결과는 [FtpCommandContractTest]가, 이 테스트는 완료 확인·크기 조회 단계와 여러 항목 작업을 검증한다.
 */
class FtpControlChannelFailureTest {

    private val dir: File = Files.createTempDirectory("ftp-control-channel-test").toFile()
    private val client = FakeFtpClient().apply { streamContent = "data".toByteArray() }
    private val session = FtpSession(client)
    private val commands = FtpCommandExecutor(session)
    private val transferrer = FtpTransferrer(session, commands)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `완료 확인 응답이 타임아웃되면 세션을 종료하고 연결 끊김으로 알린다`() {
        loggedIn()
        client.onCompletePending = { throw SocketTimeoutException("Read timed out") }

        val result = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }

        assertTrue("$result", (result as IoResult.ConnectionLost).message.contains("Read timed out"))
        assertSessionEnded()
    }

    @Test
    fun `폴더 다운로드 중 완료 확인 응답이 타임아웃되면 남은 파일을 시도하지 않는다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt", "b.txt")
        client.onCompletePending = { throw SocketTimeoutException("Read timed out") }

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true) }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertEquals(listOf("/r/a.txt"), client.retrieveRequests)
    }

    @Test
    fun `크기 조회 응답이 타임아웃되면 크기 미확인으로 넘기지 않고 세션을 종료한다`() {
        loggedIn()
        client.onMlst = { throw SocketTimeoutException("Read timed out") }

        val result = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertTrue("전송을 시작하지 않아야 함", client.retrieveRequests.isEmpty())
        assertSessionEnded()
    }

    @Test
    fun `데이터 스트림 오류 뒤 완료 응답을 정상적으로 읽었으면 그 파일만 실패로 모으고 다음 파일을 진행한다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt", "b.txt")
        client.streamReadFailure = SocketException("Connection reset")
        client.onCompletePending = { false }

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true) }

        assertEquals("다운로드 실패: 2개 항목 (a.txt, b.txt)", (result as IoResult.PartiallyFailed).message)
        assertEquals(listOf("/r/a.txt", "/r/b.txt"), client.retrieveRequests)
        assertTrue(runBlocking { session.isReady() })
        assertEquals(0, client.disconnectCalls)
    }

    @Test
    fun `폴더 삭제 중 DELE 응답이 타임아웃되면 세션을 종료하고 남은 항목을 시도하지 않는다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt", "b.txt")
        client.onDeleteFile = { throw SocketTimeoutException("Read timed out") }

        val result = runBlocking { commands.removeDirectoryRecursive("ftp://host/r") }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertEquals(1, client.deleteRequests.size)
        assertSessionEnded()
    }

    @Test
    fun `폴더 삭제 중 DELE가 오류 응답이면 그 항목만 실패로 모으고 나머지를 삭제한다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt", "b.txt")
        client.onDeleteFile = { path -> !path.endsWith("b.txt") }

        val result = runBlocking { commands.removeDirectoryRecursive("ftp://host/r") }

        assertEquals("하위 항목 삭제 실패: b.txt (DELE 실패: 550 Permission denied)", (result as IoResult.PartiallyFailed).message)
        assertEquals(2, client.deleteRequests.size)
        assertTrue(runBlocking { session.isReady() })
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark()) }

    /** 세션이 끊김 상태가 되고 소켓이 닫혔는지 확인한다. */
    private fun assertSessionEnded() {
        assertFalse(runBlocking { session.isReady() })
        assertEquals(1, client.disconnectCalls)
    }
}
