package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.OperationNotices
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 다운로드가 로컬 자리에 같은 이름의 항목이 있을 때 쓰는 시점에 묻고, 답대로 받거나 건너뛰는지 검증하는 단위 테스트(#316).
 * 묻는 것은 세션 잠금 밖에서 하며, 서버가 전송을 거부하면 이 다운로드가 만든 빈 파일만 지우고 덮어쓰기로 한 기존 파일은 비우지 않는다.
 */
class FtpTransferrerDownloadConflictTest {

    private val dir: File = Files.createTempDirectory("ftp-download-conflict-test").toFile()
    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices())
    private val local = File(dir, "a.txt")

    /** 받은 겹침(받은 순서). */
    private val asked = mutableListOf<WriteConflict>()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `같은 이름의 파일을 건너뛰면 서버에 요청하지 않고 기존 파일을 그대로 둔다`() {
        loggedIn()
        local.writeText("old")
        client.streamContent = "new".toByteArray()

        assertEquals(IoResult.Done, download(answering(ConflictChoice.SKIP)))

        assertEquals(listOf(WriteConflict("a.txt", dir.path, WriteConflictKind.FILE)), asked)
        assertEquals(emptyList<String>(), client.retrieveRequests)
        assertEquals("old", local.readText())
    }

    @Test
    fun `같은 이름의 파일을 덮어쓰기로 하면 받은 내용으로 바꾼다`() {
        loggedIn()
        local.writeText("older content")
        client.streamContent = "new".toByteArray()

        assertEquals(IoResult.Done, download(answering(ConflictChoice.PROCEED)))

        assertEquals("new", local.readText())
    }

    @Test
    fun `덮어쓰기로 했어도 서버가 전송을 거부하면 기존 파일을 비우지 않는다`() {
        loggedIn()
        local.writeText("old")

        assertTrue(download(answering(ConflictChoice.PROCEED)) is IoResult.ItemFailed)

        assertEquals("old", local.readText())
    }

    @Test
    fun `새로 만든 자리는 서버가 전송을 거부하면 지운다`() {
        loggedIn()

        assertTrue(download(answering { throw AssertionError("예상하지 못한 충돌: $it") }) is IoResult.ItemFailed)

        assertFalse(local.exists())
    }

    @Test
    fun `폴더 다운로드에서 같은 이름의 폴더를 건너뛰면 그 안을 받지 않는다`() {
        loggedIn()
        File(dir, "r").mkdir()
        client.remoteFiles = listOf("a.txt")
        client.streamContent = "new".toByteArray()

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true, resolveConflict = answering(ConflictChoice.SKIP)) }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf(WriteConflictKind.FOLDER), asked.map { it.kind })
        assertEquals(emptyList<String>(), client.retrieveRequests)
        assertFalse(File(dir, "r/a.txt").exists())
    }

    /** 세션을 연결된 상태로 둔다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }

    /** 원격 a.txt를 [local]로 받는다. */
    private fun download(resolver: WriteConflictResolver): IoResult<Unit> =
        runBlocking { transferrer.download("ftp://host/r/a.txt", local.path, isDirectory = false, resolveConflict = resolver) }

    /** 받은 겹침을 [asked]에 기록하고 [choice]로 답하는 resolver. */
    private fun answering(choice: ConflictChoice) = answering { choice }

    /** 받은 겹침을 [asked]에 기록하고 [answer]로 답하는 resolver. */
    private fun answering(answer: (WriteConflict) -> ConflictChoice) = WriteConflictResolver { conflict ->
        asked += conflict
        answer(conflict)
    }
}
