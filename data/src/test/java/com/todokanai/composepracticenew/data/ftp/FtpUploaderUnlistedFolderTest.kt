package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.assumeUnlistable
import com.todokanai.composepracticenew.datasource.localTreeBottomUp
import com.todokanai.composepracticenew.datasource.restoreListable
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.OperationNotices
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 폴더 업로드가 안을 나열하지 못한 로컬 폴더를 원격에 빈 폴더로 만들지 않고 실패로 알리는지 검증하는 단위 테스트(#422).
 * 나열을 막을 수 없는 환경(root 계정 등)에서는 건너뛴다.
 */
class FtpUploaderUnlistedFolderTest {

    /** 테스트의 로컬 파일을 두는 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-unlisted-upload-test").toFile()

    /** 업로드할 로컬 폴더. */
    private val source = File(dir, "src").apply { mkdirs(); File(this, "a.txt").writeText("A") }

    /** [source] 안의, 안을 나열하지 못하게 할 폴더. */
    private val locked = File(source, "locked").apply { mkdirs(); File(this, "hidden.txt").writeText("H") }

    /** 서버 응답을 정하고 보낸 명령을 기록하는 가짜 클라이언트. */
    private val client = FakeFtpClient()

    /** [client]를 쓰는 세션. */
    private val session = FtpSession(client)

    /** 검증 대상. */
    private val uploader = FtpOperationParts(session).uploader(OperationNotices())

    @Before
    fun setUp() {
        runBlocking { session.markLoggedIn(home = null) }
        client.streamContent = byteArrayOf(1)
    }

    @After
    fun tearDown() {
        restoreListable(locked)
        dir.localTreeBottomUp().forEach { it.file.delete() }
    }

    @Test
    fun `나열하지 못한 폴더는 원격에 만들지 않고 나머지만 올린 뒤 실패로 알린다`() {
        assumeUnlistable(locked)

        val result = runBlocking { uploader.upload(listOf(UploadSource(source.path, "ftp://host/r"))) }

        // 폴더 안쪽 실패는 바깥 묶음이 올린 항목 이름 아래로 옮긴다
        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("${source.name}/locked", FailureReason.FolderUnreadable()))), result)
        assertEquals(listOf("/r/a.txt"), client.storeRequests)
        assertFalse("/r/locked" in client.makeDirectoryRequests)
    }
}
