package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UNSUPPORTED_LINK_REASON
import com.todokanai.composepracticenew.datasource.assumeSymlink
import com.todokanai.composepracticenew.datasource.localTreeBottomUp
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import com.todokanai.composepracticenew.repository.OperationNotices
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 업로드가 심볼릭 링크를 따라가지 않고 건너뛰어 실패로 알리는지 검증하는 단위 테스트(#320).
 * 링크를 만들 수 없는 환경에서는 건너뛴다.
 */
class FtpTransferrerSymlinkUploadTest {

    /** 테스트의 로컬 파일을 두는 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-symlink-upload-test").toFile()

    /** 업로드할 로컬 폴더. */
    private val source = File(dir, "src").apply { mkdirs(); File(this, "a.txt").writeText("A") }

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
        // 링크를 따라가지 않고 지운다 — 순환 링크가 있어도 끝나도록
        dir.localTreeBottomUp().forEach { it.file.delete() }
    }

    @Test
    fun `올릴 항목 자신이 링크면 STOR를 보내지 않고 실패한다`() {
        val link = File(dir, "link")
        assumeSymlink(link, File(source, "a.txt").absoluteFile)

        val result = runBlocking { transferrer.upload(link.path, "ftp://host/r/link") }

        assertEquals(IoResult.ItemFailed(UNSUPPORTED_LINK_REASON), result)
        assertEquals(emptyList<String>(), client.storeRequests)
    }

    @Test
    fun `폴더 안의 순환 링크는 따라가지 않고 나머지만 올린 뒤 실패로 알린다`() {
        assumeSymlink(File(source, "loop"), source)

        val result = runBlocking { transferrer.upload(source.path, "ftp://host/r") }

        assertEquals(IoResult.PartiallyFailed("업로드 실패", listOf(FailedItem("loop", UNSUPPORTED_LINK_REASON))), result)
        assertEquals(listOf("/r/a.txt"), client.storeRequests)
    }
}
