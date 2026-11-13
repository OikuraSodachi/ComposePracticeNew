package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import com.todokanai.composepracticenew.repository.OperationNotices
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 단일 파일 다운로드·업로드가 파일 하나짜리 작업으로 폴더 전송과 같은 진행률 규칙을 따르고(#242), 여러 항목 업로드가 묶음 전체로 보고하는지(#356) 검증하는 단위 테스트. */
class FtpTransferrerFileProgressTest {

    /** 받을 파일과 올릴 파일을 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-file-progress-test").toFile()
    /** 데이터 스트림을 주고받는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { streamContent = "data".toByteArray() }
    /** 검증 대상 세션. */
    private val session = FtpSession(client, ConnectionLossNotices()).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }
    /** 검증 대상 전송기. */
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices())
    /** 보고된 진행 상태. */
    private val reports = mutableListOf<ProgressState>()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `크기를 아는 파일을 받으면 완료 응답 전에는 99까지만 보고하고 완료 응답 뒤에 100%를 보고한다`() {
        client.onMlst = { mlstReply(size = 4) }

        assertEquals(IoResult.Done, download())

        assertEquals(listOf(99, 100), reports.map { it.progress })
        assertTrue(reports.all { it.totalBytes == 4L && it.listSize == 1 })
    }

    @Test
    fun `0바이트 파일을 받아도 완료 응답 뒤에 100%를 보고한다`() {
        client.streamContent = ByteArray(0)
        client.onMlst = { mlstReply(size = 0) }

        assertEquals(IoResult.Done, download())

        assertEquals(100, reports.single().progress)
        assertEquals(0L, reports.single().totalBytes)
    }

    @Test
    fun `크기를 모르는 파일을 받으면 퍼센트 없이 크기를 모르는 파일 하나로 보고하고 끝나면 받은 바이트로 100%를 보고한다`() {
        assertEquals(IoResult.Done, download())

        val inProgress = reports.first()
        assertNull(inProgress.progress)
        assertEquals(KnownSizeProgress(knownTotalBytes = 0, knownWrittenBytes = 0, knownCompletedBytes = 0, unknownFileCount = 1, unknownWrittenBytes = 4), inProgress.knownSize)
        assertEquals(100, reports.last().progress)
        assertEquals(4L, reports.last().totalBytes)
    }

    @Test
    fun `0바이트 파일을 올려도 완료 응답 뒤에 100%를 보고한다`() {
        val empty = File(dir, "empty.txt").apply { writeBytes(ByteArray(0)) }

        assertEquals(IoResult.Done, runBlocking { transferrer.upload(listOf(UploadSource(empty.path, "ftp://host/r/empty.txt")), onProgress = { reports += it }) })

        assertEquals(100, reports.single().progress)
        assertEquals(0L, reports.single().totalBytes)
    }

    @Test
    fun `여러 항목을 올리면 진행률을 묶음 전체로 보고하고 항목마다 처음으로 돌아가지 않는다`() {
        // 항목마다 따로 계산하면 진행 막대가 항목 수만큼 0→100%를 반복한다(#356)
        val first = File(dir, "a.txt").apply { writeBytes(ByteArray(3)) }
        val second = File(dir, "b.txt").apply { writeBytes(ByteArray(4)) }

        val result = runBlocking {
            transferrer.upload(listOf(UploadSource(first.path, "ftp://host/r/a.txt"), UploadSource(second.path, "ftp://host/r/b.txt")), onProgress = { reports += it })
        }

        assertEquals(IoResult.Done, result)
        assertTrue(reports.toString(), reports.all { it.totalBytes == 7L && it.listSize == 2 })
        val progresses = reports.mapNotNull { it.progress }
        assertEquals("진행률이 줄어들면 안 됨: $progresses", progresses.sorted(), progresses)
        assertEquals(100, progresses.last())
    }

    /** [size] 바이트 파일을 알리는 MLST 응답 줄을 만든다. */
    private fun mlstReply(size: Long) = listOf("250-Listing a.txt", " type=file;size=$size; a.txt", "250 End")

    /** 원격 파일 하나를 받는다. */
    private fun download(): IoResult<Unit> = runBlocking {
        // 받기 전 준비 단계(#284)는 빼고 전송 단계의 진행만 본다
        transferrer.download(sources = listOf(DownloadSource("ftp://host/r/a.txt", File(dir, "a.txt").path, false)), resolveConflict = UnexpectedConflictResolver, onProgress = { if (it.preparing == null) reports += it })
    }
}
