package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * 전송의 로컬 파일 실패가 세션 종료로 잘못 분류되지 않는지, 폴더 업로드의 원격 폴더 생성 규칙이 유지되는지 검증하는 단위 테스트(#233).
 */
class FtpTransferrerLocalFileFailureTest {

    private val dir: File = Files.createTempDirectory("ftp-local-failure-test").toFile()
    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session))

    @Before
    fun setUp() {
        runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
        client.streamContent = byteArrayOf(1)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `열 수 없는 로컬 파일을 올리면 STOR를 보내지 않고 이 항목만 실패한다`() {
        val result = runBlocking { transferrer.upload(File(dir, "missing.txt").path, "ftp://host/r/a.txt") }

        assertTrue("로컬 파일 실패는 이 항목의 실패여야 함: $result", result is IoResult.ItemFailed)
        assertEquals("서버 파일을 비우는 STOR를 보내면 안 됨", emptyList<String>(), client.storeRequests)
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `열 수 있는 로컬 파일은 STOR로 올린다`() {
        val local = File(dir, "a.txt").apply { writeText("A") }

        val result = runBlocking { transferrer.upload(local.path, "ftp://host/r/a.txt") }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf("/r/a.txt"), client.storeRequests)
    }

    @Test
    fun `쓸 수 없는 로컬 경로로 받으면 이 항목만 실패하고 세션은 유지된다`() {
        val directoryAsTarget = File(dir, "target").apply { mkdirs() }

        val result = runBlocking { transferrer.download("ftp://host/r/a.txt", directoryAsTarget.path) }

        assertTrue("로컬 파일 실패는 이 항목의 실패여야 함: $result", result is IoResult.ItemFailed)
        assertTrue("로컬 파일 실패로 세션이 끝나면 안 됨", runBlocking { session.isReady() })
    }

    @Test
    fun `폴더 업로드는 원격 폴더 생성이 거부돼도 안의 파일을 올린다`() {
        val folder = File(dir, "r").apply { mkdirs() }
        File(folder, "a.txt").writeText("A")
        client.serverBehaviors = mapOf("MKD" to ServerBehavior.Refuse(550, "550 Already exists"))

        val result = runBlocking { transferrer.upload(folder.path, "ftp://host/r") }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf("/r"), client.makeDirectoryRequests)
        assertEquals(listOf("/r/a.txt"), client.storeRequests)
    }

    @Test
    fun `폴더 업로드 중 원격 폴더 생성에서 연결이 끊기면 남은 항목을 시도하지 않는다`() {
        val folder = File(dir, "r").apply { mkdirs() }
        File(folder, "a.txt").writeText("A")
        client.serverBehaviors = mapOf("MKD" to ServerBehavior.Fail(IOException("read timed out")))

        val result = runBlocking { transferrer.upload(folder.path, "ftp://host/r") }

        assertTrue("연결 끊김은 숨기면 안 됨: $result", result is IoResult.ConnectionLost)
        assertEquals(emptyList<String>(), client.storeRequests)
        assertFalse(runBlocking { session.isReady() })
    }
}
