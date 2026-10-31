package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 폴더 다운로드가 크기를 모르는 파일이 있으면 아는 크기만 더한 합계로 진행률을 지어내지 않고, 진행률 없이 보고하는지 검증하는 단위 테스트. */
class FtpTransferrerDirectoryProgressTest {

    /** 받은 폴더를 저장할 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-directory-progress-test").toFile()
    /** 파일마다 4바이트를 내려주는 MLSD 지원 가짜 클라이언트. */
    private val client = FakeFtpClient().apply {
        streamContent = "data".toByteArray()
        features = setOf("MLST")
    }
    /** 검증 대상 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null) } }
    /** 검증 대상 전송기. */
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session))
    /** 보고된 진행 상태. */
    private val reports = mutableListOf<ProgressState>()

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
        assertTrue(inProgress.all { it.progress == null && it.totalBytes == null })
        assertEquals(KnownSizeProgress(knownTotalBytes = 4, knownWrittenBytes = 4, unknownFileCount = 1, unknownWrittenBytes = 0), inProgress.first().knownSize)
        assertEquals(100, reports.last().progress)
        assertEquals(8L, reports.last().totalBytes)
    }

    @Test
    fun `모든 파일의 크기를 알면 합계에 대한 퍼센트로 보고한다`() {
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=4; a.txt", "type=file;size=4; b.txt"))

        assertEquals(IoResult.Done, download())

        assertEquals(listOf(50, 100), reports.map { it.progress })
        assertTrue(reports.all { it.totalBytes == 8L })
    }

    private fun download(): IoResult<Unit> = runBlocking {
        transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true, onProgress = { reports += it })
    }
}
