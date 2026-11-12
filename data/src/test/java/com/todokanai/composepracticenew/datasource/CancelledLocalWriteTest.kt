package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.sf.sevenzipjbinding.ExtractAskMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 취소된 로컬 쓰기 작업(복사·압축·압축 해제)이 쓰다 만 파일만 지우고 이미 다 쓴 파일은 남기며, 취소를 결과로 바꾸지 않고 전파하는지 검증하는 단위 테스트(#236).
 * 취소는 오케스트레이터처럼 진행 보고 콜백이 CancellationException을 던지는 것으로 흉내 낸다 — b.txt를 쓰는 도중의 보고에서 던진다.
 */
class CancelledLocalWriteTest {

    /** 원본과 결과를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("cancelled-local-write-test").toFile()
    /** 먼저 다 쓰는 파일. */
    private val a = File(dir, "a.txt").apply { writeText("AAAA") }
    /** 쓰는 도중에 취소되는 파일. */
    private val b = File(dir, "b.txt").apply { writeText("BBBB") }
    /** 결과를 둘 디렉터리. */
    private val out = File(dir, "out").apply { mkdirs() }

    /** b.txt를 쓰는 도중의 보고에서 취소를 던지는 진행 보고 콜백. */
    private val cancelOnB: (ProgressState) -> Unit = { if (it.currentFileName == "b.txt") throw CancellationException("취소") }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `복사를 취소하면 쓰던 파일만 지우고 이미 복사한 파일은 남긴다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE })

        assertThrows(CancellationException::class.java) { runBlocking { transfer.copy(linkedSetOf(a.path, b.path), out.path, UnexpectedConflictResolver, cancelOnB) } }

        assertEquals("AAAA", File(out, "a.txt").readText())
        assertFalse(File(out, "b.txt").exists())
    }

    @Test
    fun `압축을 취소하면 만들던 압축 파일을 지운다`() {
        val compression = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to ZipArchiveCompressor()))

        assertThrows(CancellationException::class.java) {
            runBlocking { compression.compress(linkedSetOf(a.path, b.path), out.path, "archive", ArchiveFormat.ZIP, cancelOnB) }
        }

        assertFalse(File(out, "archive.zip").exists())
    }

    @Test
    fun `압축을 다 쓴 직후의 완료 보고에서 취소돼도 압축 파일을 지운다`() {
        val compression = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to ZipArchiveCompressor()))

        assertThrows(CancellationException::class.java) {
            runBlocking {
                compression.compress(linkedSetOf(a.path, b.path), out.path, "archive", ArchiveFormat.ZIP) {
                    if (it.progress == 100) throw CancellationException("취소")
                }
            }
        }

        assertFalse(File(out, "archive.zip").exists())
    }

    @Test
    fun `ZIP 해제를 취소하면 쓰던 항목만 지우고 이미 푼 항목은 남긴다`() {
        val zip = File(dir, "src.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            listOf(a, b).forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                zos.write(file.readBytes())
                zos.closeEntry()
            }
        }

        assertThrows(CancellationException::class.java) {
            ZipArchiveExtractor().extract(zip.path, ArchiveEntryWriter(ExtractionRoot(out), UnexpectedConflictAsk), ByteProgressTracker(8, 2, cancelOnB))
        }

        assertEquals("AAAA", File(out, "a.txt").readText())
        assertFalse(File(out, "b.txt").exists())
    }

    @Test
    fun `RAR 해제를 취소하면 닫을 때 쓰던 항목을 지운다`() {
        val callback = RarExtractCallback({ RarEntryKind.File("b.txt") }, { 4L }, ArchiveEntryWriter(ExtractionRoot(out), UnexpectedConflictAsk), ByteProgressTracker(4, 1, cancelOnB))

        // 라이브러리처럼 기록 중 던진 예외를 받아 추출을 멈추고 콜백을 닫는다
        val stream = callback.getStream(0, ExtractAskMode.EXTRACT)!!
        assertThrows(CancellationException::class.java) { stream.write("BB".toByteArray()) }
        callback.close()

        assertFalse(File(out, "b.txt").exists())
    }
}
