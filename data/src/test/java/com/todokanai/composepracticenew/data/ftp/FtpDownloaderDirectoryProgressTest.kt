package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.testviews.*
import com.todokanai.composepracticenew.datasource.UnexpectedLeftoverDiscarder
import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.model.ByteAmount
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.ProgressPhase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 폴더 다운로드가 크기를 모르는 파일이 있으면 아는 크기만 더한 합계로 진행률을 지어내지 않고, 진행률 없이 보고하는지 검증하는 단위 테스트. */
class FtpDownloaderDirectoryProgressTest {

    /** 받은 폴더를 저장할 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-directory-progress-test").toFile()
    /** 파일마다 4바이트를 내려주는 MLSD 지원 가짜 클라이언트. */
    private val client = FakeFtpClient().apply {
        streamContent = "data".toByteArray()
        features = setOf("MLST")
    }
    /** 검증 대상 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(home = null) } }
    /** 검증 대상 다운로더. */
    private val downloader = FtpOperationParts(session).downloader(UnexpectedLeftoverDiscarder)
    /** 보고된 진행 상태. */
    private val reports = mutableListOf<ProgressPhase>()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `크기를 모르는 파일이 있으면 마칠 때까지 진행률 없이 보고하고 끝나면 100%다`() {
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=4; known.txt", "type=file; unknown.bin"))

        assertEquals(IoResult.Done, download())

        val inProgress = reports.dropLast(1)
        assertTrue(inProgress.isNotEmpty())
        inProgress.forEach { it.asPartlyUnknown() }
        assertEquals(KnownSizeProgress(knownTotalBytes = 4, knownWrittenBytes = 4, knownCompletedBytes = 0, unknownFileCount = 1, unknownWrittenBytes = 0), inProgress.first().asPartlyUnknown().knownSize)
        assertEquals(ByteAmount.Known(100, 8), reports.last().asBytes().amount)
    }

    @Test
    fun `받기 전 트리를 수집하는 동안 준비 단계를 알린다`() {
        // 수집이 오래 걸려도 진행이 보이고, 그 보고에서 취소를 받도록(#284)
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=4; a.txt"))
        val all = mutableListOf<ProgressPhase>()

        runBlocking { downloader.download(sources = listOf(DownloadSource("ftp://host/r", File(dir, "r").path, true)), resolveConflict = UnexpectedConflictResolver, onProgress = { all += it }) }

        assertEquals(IndeterminatePhase.Preparing(1, "a.txt"), all.first())
        assertTrue(all.drop(1).all { it is ProgressPhase.Bytes })
    }

    @Test
    fun `모든 파일의 크기를 알면 합계에 대한 퍼센트로 보고하고 100%는 모든 파일을 받은 뒤에 보고한다`() {
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=4; a.txt", "type=file;size=4; b.txt"))

        assertEquals(IoResult.Done, download())

        assertEquals(listOf(50, 99, 100), reports.map { it.asKnown().percent })
        assertTrue(reports.all { it.asKnown().totalBytes == 8L })
    }

    @Test
    fun `앞선 파일이 실패하면 마지막 파일의 완료 응답을 받아도 100%를 보고하지 않는다`() {
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=4; a.txt", "type=file;size=4; b.txt"))
        client.onCompletePending = { client.completePendingCalls != 1 }

        assertTrue(download() is IoResult.PartiallyFailed)

        assertTrue(reports.none { it.asKnown().percent == 100 })
    }

    private fun download(): IoResult<Unit> = runBlocking {
        // 받기 전 준비 단계(#284)는 빼고 전송 단계의 진행만 본다
        downloader.download(sources = listOf(DownloadSource("ftp://host/r", File(dir, "r").path, true)), resolveConflict = UnexpectedConflictResolver, onProgress = { if (it !is IndeterminatePhase) reports += it })
    }
}
