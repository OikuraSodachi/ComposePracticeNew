package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP 안의 심볼릭 링크 항목을 [ZipArchiveExtractor]가 링크 대상 경로를 내용으로 한 파일로 풀지 않고 실패로 알리는지 검증하는 단위 테스트(#424).
 * Info-ZIP(`zip -y`)이 만든 ZIP처럼 링크 항목의 외부 속성 위쪽 16비트에 Unix 링크 모드를 담는다.
 */
class ZipSymlinkEntryTest {

    /** 아카이브와 목적지를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("zip-symlink-test").toFile()
    /** 압축 해제 목적지. */
    private val dest: File = File(dir, "dest").apply { mkdirs() }
    /** 검증 대상. ZIP 추출기만 둔다. */
    private val extraction = LocalArchiveExtraction(setOf(ZipArchiveExtractor()), UnexpectedLeftoverDiscarder)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `링크 항목은 만들지 않고 실패로 알리며 나머지 항목은 푼다`() {
        val zip = zipOf("a.txt" to REGULAR_FILE_MODE, "d/link" to SYMLINK_MODE)

        val result = runBlocking { extraction.unzip(setOf(zip.path), dest.path, ExtractLayout.IntoDestination, UnexpectedConflictResolver, UnexpectedPasswordProvider) {} }

        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("d/link", FailureReason.UnsupportedLink()))), result)
        assertEquals("a.txt", File(dest, "a.txt").readText())
        assertFalse(File(dest, "d/link").exists())
    }

    @Test
    fun `외부 속성의 Unix 모드가 링크일 때만 링크로 판별한다`() {
        assertTrue(isZipSymlink(attributesOf(SYMLINK_MODE)))
        assertFalse(isZipSymlink(attributesOf(REGULAR_FILE_MODE)))
        assertFalse(isZipSymlink(attributesOf(DIRECTORY_MODE)))
        // Windows에서 만든 ZIP: 위쪽 16비트 없이 MS-DOS 속성(보관)만 담는다
        assertFalse(isZipSymlink(byteArrayOf(0x20, 0, 0, 0)))
        assertFalse(isZipSymlink(null))
        assertFalse(isZipSymlink(byteArrayOf(0, 0)))
    }

    /**
     * 항목 이름을 내용으로 한 항목들을 담은 ZIP을 만들고, 중앙 디렉터리의 외부 속성을 항목마다 [entries]의 Unix 모드로 바꾼다.
     * java.util.zip은 외부 속성을 정할 수 없어 쓴 뒤에 바꾼다.
     * @param entries 항목 이름과 그 Unix 모드
     */
    private fun zipOf(vararg entries: Pair<String, Int>): File {
        val file = File(dir, "a.zip")
        ZipOutputStream(file.outputStream()).use { zos ->
            entries.forEach { (name, _) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(name.toByteArray())
                zos.closeEntry()
            }
        }
        val bytes = file.readBytes()
        centralHeaderOffsetsOf(bytes).zip(entries) { offset, (_, mode) ->
            attributesOf(mode).copyInto(bytes, offset + EXTERNAL_ATTRIBUTES_OFFSET)
        }
        file.writeBytes(bytes)
        return file
    }

    /**
     * 중앙 디렉터리 항목 머리의 시작 위치를 담긴 순서대로 찾는다.
     * @param bytes ZIP 파일 내용
     */
    private fun centralHeaderOffsetsOf(bytes: ByteArray): List<Int> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return (0..bytes.size - 4).filter { buffer.getInt(it) == CENTRAL_HEADER_SIGNATURE }
    }

    /**
     * Unix 모드를 위쪽 16비트에 담은 외부 속성 바이트(리틀 엔디언)를 만든다.
     * @param mode Unix 파일 모드
     */
    private fun attributesOf(mode: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(mode shl 16).array()

    private companion object {
        /** 중앙 디렉터리 항목 머리의 서명. */
        const val CENTRAL_HEADER_SIGNATURE = 0x02014b50
        /** 중앙 디렉터리 항목 머리 안에서 외부 속성의 위치. */
        const val EXTERNAL_ATTRIBUTES_OFFSET = 38
        /** 권한 0777의 심볼릭 링크 모드(`lrwxrwxrwx`). */
        const val SYMLINK_MODE = 0xA1FF
        /** 권한 0644의 일반 파일 모드. */
        const val REGULAR_FILE_MODE = 0x81A4
        /** 권한 0755의 디렉터리 모드. */
        const val DIRECTORY_MODE = 0x41ED
    }
}
