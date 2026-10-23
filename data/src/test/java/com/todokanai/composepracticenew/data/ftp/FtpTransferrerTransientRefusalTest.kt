package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 데이터 스트림 요청이 4xx(일시 거부)로 거부돼도 서버가 응답한 것이므로 연결 끊김으로 보지 않고,
 * 세션을 유지한 채 그 파일만 서버 응답을 담아 실패시키며 폴더 작업은 남은 파일을 계속 진행하는지 검증하는 단위 테스트.
 */
class FtpTransferrerTransientRefusalTest {

    private val dir: File = Files.createTempDirectory("ftp-transient-refusal-test").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `다운로드 스트림이 4xx로 거부되면 세션을 유지하고 서버 응답을 담아 그 파일만 실패한다`() {
        listOf(
            ServerBehavior.Refuse(425, "425 Can't open data connection"),
            ServerBehavior.Refuse(450, "450 File busy"),
            ServerBehavior.Refuse(452, "452 Insufficient storage")
        ).forEach { refusal ->
            val (client, session, transferrer) = connectedTransferrer(refusal)

            val result = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }

            assertEquals("${refusal.code}", IoResult.ItemFailed("retrieveFileStream 실패: ${refusal.text}"), result)
            assertTrue("${refusal.code}: 세션이 유지돼야 함", runBlocking { session.isReady() })
            assertEquals(0, client.disconnectCalls)
        }
    }

    @Test
    fun `업로드 스트림이 452로 거부되면 세션을 유지하고 그 파일만 실패한다`() {
        val (client, session, transferrer) = connectedTransferrer(ServerBehavior.Refuse(452, "452 Insufficient storage"))
        val local = File(dir, "a.txt").apply { writeText("A") }

        val result = runBlocking { transferrer.upload(local.path, "ftp://host/r/a.txt") }

        assertEquals(IoResult.ItemFailed("storeFileStream 실패: 452 Insufficient storage"), result)
        assertTrue(runBlocking { session.isReady() })
        assertEquals(0, client.disconnectCalls)
    }

    @Test
    fun `폴더 다운로드 중 425로 거부돼도 연결 끊김으로 멈추지 않고 모든 파일을 시도한 뒤 실패를 모은다`() {
        val (client, session, transferrer) = connectedTransferrer(ServerBehavior.Refuse(425, "425 Can't open data connection"))
        client.remoteFiles = listOf("a.txt", "b.txt")

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true) }

        assertEquals(setOf("a.txt", "b.txt"), (result as IoResult.PartiallyFailed).failures.map { it.path }.toSet())
        assertEquals(listOf("/r/a.txt", "/r/b.txt"), client.retrieveRequests)
        assertTrue(runBlocking { session.isReady() })
        assertEquals(0, client.disconnectCalls)
    }

    @Test
    fun `폴더 다운로드에서 LIST가 450으로 거부되면 빈 폴더로 성공하지 않고 실패한다`() {
        val (client, session, transferrer) = connectedTransferrer(ServerBehavior.Refuse(550, "550 Permission denied"))
        client.streamContent = "data".toByteArray()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(450, "450 File busy"))

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true) }

        assertEquals(IoResult.ItemFailed("LIST 실패: 450 File busy"), result)
        assertTrue(runBlocking { session.isReady() })
    }

    /**
     * 데이터 스트림 요청을 [refusal] 응답으로 거부하는 가짜 클라이언트에 로그인된 세션과 전송기를 만든다.
     * @param refusal 스트림 거부 응답
     * @return 가짜 클라이언트, 세션, 전송기
     */
    private fun connectedTransferrer(refusal: ServerBehavior.Refuse): Triple<FakeFtpClient, FtpSession, FtpTransferrer> {
        val client = FakeFtpClient().apply { streamRefusal = refusal }
        val session = FtpSession(client).also { runBlocking { it.markLoggedIn() } }
        return Triple(client, session, FtpTransferrer(session, FtpCommandExecutor(session)))
    }
}
