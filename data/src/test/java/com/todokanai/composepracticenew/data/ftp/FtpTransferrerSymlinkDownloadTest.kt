package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UNSUPPORTED_LINK_REASON
import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.datasource.assumeSymlink
import com.todokanai.composepracticenew.datasource.localTreeBottomUp
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import com.todokanai.composepracticenew.repository.OperationNotices
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 다운로드가 로컬 목적지의 같은 이름 심볼릭 링크를 따라가지 않고, 묻지 않고 건너뛰어 실패로 알리는지 검증하는 단위 테스트(#338).
 * 링크를 만들 수 없는 환경에서는 건너뛴다.
 */
class FtpTransferrerSymlinkDownloadTest {

    /** 테스트의 로컬 파일을 두는 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-symlink-download-test").toFile()

    /** 링크가 가리킬, 다운로드 목적지 밖의 파일. */
    private val outsideFile = File(dir, "keep.txt").apply { writeText("K") }

    /** 서버 응답을 정하고 보낸 명령을 기록하는 가짜 클라이언트. */
    private val client = FakeFtpClient()

    /** [client]를 쓰는 세션. */
    private val session = FtpSession(client, ConnectionLossNotices())

    /** 검증 대상. */
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices())

    @Before
    fun setUp() {
        runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
        client.streamContent = byteArrayOf(1)
    }

    @After
    fun tearDown() {
        // 링크를 따라가지 않고 지운다 — 링크 대상을 두 번 지우지 않도록
        dir.localTreeBottomUp().forEach { it.file.delete() }
    }

    @Test
    fun `받을 자리가 링크면 RETR를 보내지 않고 링크가 가리키는 파일을 비우지 않는다`() {
        val link = File(dir, "a.txt")
        assumeSymlink(link, outsideFile.absoluteFile)

        val result = runBlocking { transferrer.download(sources = listOf(DownloadSource("ftp://host/r/a.txt", link.path, false)), resolveConflict = UnexpectedConflictResolver).singleItemResult() }

        assertEquals(IoResult.ItemFailed(UNSUPPORTED_LINK_REASON), result)
        assertEquals(emptyList<String>(), client.retrieveRequests)
        assertEquals("K", outsideFile.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
    }

    @Test
    fun `폴더 다운로드는 안쪽 자리의 링크만 건너뛰고 나머지를 받은 뒤 실패로 알린다`() {
        client.remoteFiles = listOf("a.txt", "b.txt")
        val localRoot = File(dir, "r").apply { mkdirs() }
        assumeSymlink(File(localRoot, "a.txt"), outsideFile.absoluteFile)

        val result = runBlocking { transferrer.download(sources = listOf(DownloadSource("ftp://host/r", localRoot.path, true)), resolveConflict = ProceedingConflictResolver) }

        assertEquals(IoResult.PartiallyFailed("다운로드 실패", listOf(FailedItem("r/a.txt", UNSUPPORTED_LINK_REASON))), result)
        assertEquals(listOf("/r/b.txt"), client.retrieveRequests)
        assertEquals("K", outsideFile.readText())
    }

    /** 같은 이름의 항목을 만나면 언제나 진행(폴더는 합치기)을 고르는 충돌 판정자. */
    private val ProceedingConflictResolver = WriteConflictResolver { ConflictChoice.PROCEED }
}
