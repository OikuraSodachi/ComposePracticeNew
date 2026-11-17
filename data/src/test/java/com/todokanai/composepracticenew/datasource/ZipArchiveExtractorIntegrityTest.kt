package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [ZipArchiveExtractor]가 내용이 central directory의 CRC·크기와 다른 항목을 성공으로 풀지 않고 IOException으로 멈추는지 검증하는 단위 테스트(#326 리뷰).
 * zip4j가 항목 끝에서 CRC를 검증하는지 확인하기 위해 압축하지 않은(STORED) 항목의 데이터를 바꿔 손상을 만든다.
 */
class ZipArchiveExtractorIntegrityTest {

    /** 아카이브와 압축 해제 결과를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("zip-extractor-integrity-test").toFile()
    /** 압축 해제 목적지. */
    private val out = File(dir, "out")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `손상되지 않은 항목은 그대로 푼다`() {
        val zip = storedZipOf(CONTENT)

        extract(zip)

        assertEquals(CONTENT, File(out, "a.txt").readText())
    }

    @Test
    fun `데이터가 바뀌어 CRC가 맞지 않는 항목은 항목 이름을 담은 IOException으로 멈춘다`() {
        val zip = storedZipOf(CONTENT).also { corrupt(it) }

        val thrown = assertThrows(IOException::class.java) { extract(zip) }

        assertTrue(thrown.message!!.contains("a.txt"))
        assertTrue(thrown.message!!.contains("crc", ignoreCase = true))
    }

    @Test
    fun `0바이트 파일은 빈 아카이브로 풀지 않고 크기 산정과 압축 해제 모두 IOException으로 멈춘다`() {
        // zip4j는 0바이트 파일을 새로 만들 빈 아카이브로 열어, 확인하지 않으면 항목 없는 성공으로 끝난다
        val zip = File(dir, "empty.zip").apply { createNewFile() }

        assertThrows(IOException::class.java) { ZipArchiveExtractor().measure(zip.path, unexpectedPasswordSession()) }
        assertThrows(IOException::class.java) { extract(zip) }
    }

    @Test
    fun `끝 레코드만 있는 정상적인 빈 ZIP은 실패하지 않고 아무것도 풀지 않는다`() {
        val zip = File(dir, "empty.zip").apply { writeBytes(EMPTY_ZIP) }

        extract(zip)

        assertEquals(emptyList<File>(), out.listFiles().orEmpty().toList())
    }

    /** [zip]을 [out]에 푼다. */
    private fun extract(zip: File) = ByteProgressTracker(1, 1, onProgress = {}).let { tracker ->
        ZipArchiveExtractor().extract(zip.path, ArchiveEntryWriter(ExtractionRoot(out), UnexpectedConflictAsk, tracker), unexpectedPasswordSession(), tracker)
    }

    /** [content]를 압축하지 않고(STORED) 담은 항목 `a.txt` 하나짜리 ZIP 파일을 만든다. */
    private fun storedZipOf(content: String): File =
        File(dir, "test.zip").also { file ->
            val bytes = content.toByteArray()
            ZipOutputStream(file.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("a.txt").apply { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; crc = crcOf(content) })
                zos.write(bytes)
                zos.closeEntry()
            }
        }

    /** [zip] 안에 그대로 저장된 [CONTENT]의 첫 바이트를 바꾼다. 헤더는 건드리지 않으므로 CRC만 맞지 않게 된다. */
    private fun corrupt(zip: File) {
        val bytes = zip.readBytes()
        val at = String(bytes, Charsets.ISO_8859_1).indexOf(CONTENT)
        bytes[at] = 'X'.code.toByte()
        zip.writeBytes(bytes)
    }

    /** [content]의 CRC32 값. */
    private fun crcOf(content: String): Long = CRC32().apply { update(content.toByteArray()) }.value

    private companion object {
        /** 헤더와 겹치지 않도록 고른 항목 내용. */
        const val CONTENT = "integrity-check-content"
        /** 항목이 없는 정상 ZIP — 끝 레코드(서명 `PK\u0005\u0006` + 0으로 채운 18바이트)만 있는 22바이트. */
        val EMPTY_ZIP = byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18)
    }
}
