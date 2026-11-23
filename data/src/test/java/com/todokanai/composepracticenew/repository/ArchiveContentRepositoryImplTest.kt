package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.UnexpectedPasswordProvider
import com.todokanai.composepracticenew.datasource.ZipArchiveExtractor
import com.todokanai.composepracticenew.model.ArchiveEntry
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * [ArchiveContentRepositoryImpl]이 ZIP의 항목 목록을 내용을 풀지 않고 폴더 트리로 읽고, 내용만 암호화한 항목은 묻지 않으며,
 * 읽지 못하는 압축 파일(0바이트, UTF-8이 아닌 이름, 빠진 볼륨, 분할 압축의 다른 볼륨)을 빈 목록이 아니라 실패로 돌려주는지 zip4j로 검증하는 통합 테스트.
 */
class ArchiveContentRepositoryImplTest {

    /** 압축 파일을 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("archive-content-test").toFile()
    /** 검증 대상. ZIP 추출기만 둔다. */
    private val repository = ArchiveContentRepositoryImpl(setOf(ZipArchiveExtractor()))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `단일 아카이브와 시작 볼륨만 내용을 볼 수 있다`() {
        assertTrue(repository.canView("/x/a.zip"))
        assertTrue(repository.canView("/x/a.zip.001"))
        assertFalse(repository.canView("/x/a.zip.002"))
        assertFalse(repository.canView("/x/a.z01"))
        assertFalse(repository.canView("/x/a.txt"))
    }

    @Test
    fun `폴더 항목이 없는 ZIP도 경로에서 폴더를 만들어 크기와 함께 읽는다`() {
        val zip = javaZip("a.zip", "dir/inner.txt" to "hello", "top.txt" to "x")

        val root = contentsOf(zip)

        val folder = root.entries[0] as ArchiveEntry.Folder
        assertEquals("dir", folder.name)
        val inner = folder.contents.entries.single() as ArchiveEntry.File
        assertEquals("inner.txt", inner.name)
        assertEquals(5L, inner.sizeBytes)
        assertFalse(inner.isEncrypted)
        assertEquals("top.txt", root.entries[1].name)
        assertEquals(1L, root.entries[1].sizeBytes)
    }

    @Test
    fun `내용만 암호화한 항목은 비밀번호를 묻지 않고 암호화됨으로 읽는다`() {
        val zip = File(dir, "secret.zip")
        val source = File(Files.createTempDirectory(dir.toPath(), "src").toFile(), "s.txt").apply { writeText("secret") }
        ZipFile(zip, "pw".toCharArray()).use { zf ->
            zf.addFile(source, ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            })
        }

        val entry = contentsOf(zip).entries.single() as ArchiveEntry.File

        assertEquals("s.txt", entry.name)
        assertTrue(entry.isEncrypted)
    }

    @Test
    fun `0바이트 파일은 빈 목록이 아니라 읽지 못하는 압축 파일로 실패한다`() {
        val zip = File(dir, "empty.zip").apply { createNewFile() }

        assertEquals(IoResult.ItemFailed(FailureReason.UnreadableArchive("empty.zip")), result(zip))
    }

    @Test
    fun `UTF-8이 아닌 이름이 있으면 목록 전체가 실패한다`() {
        val zip = File(dir, "a.zip")
        ZipOutputStream(zip.outputStream(), Charset.forName("MS949")).use { zos ->
            zos.putNextEntry(ZipEntry("한글.txt"))
            zos.write("x".toByteArray())
            zos.closeEntry()
        }

        assertEquals(IoResult.ItemFailed(FailureReason.ArchiveEntryNameUnreadable), result(zip))
    }

    @Test
    fun `번호 분할의 가운데 볼륨이 빠지면 빠진 볼륨으로 실패한다`() {
        // 압축되지 않는 내용으로 볼륨이 셋 이상 생기게 한다
        val whole = javaZip("whole.tmp", "big.bin" to Random(1).let { r -> String(CharArray(6000) { 'a' + r.nextInt(26) }) })
        assertTrue(whole.length() > 2000)
        whole.readBytes().asList().chunked(1000).forEachIndexed { i, chunk -> File(dir, "a.zip.%03d".format(i + 1)).writeBytes(chunk.toByteArray()) }
        File(dir, "a.zip.002").delete()

        assertEquals(IoResult.ItemFailed(FailureReason.MissingArchiveVolume("a.zip.001", "a.zip.002")), result(File(dir, "a.zip.001")))
    }

    @Test
    fun `분할 압축의 다른 볼륨은 읽지 않고 지원하지 않는 형식으로 실패한다`() {
        val later = File(dir, "a.zip.002").apply { writeBytes(byteArrayOf(1)) }

        assertEquals(IoResult.ItemFailed(FailureReason.UnsupportedArchiveFormat("a.zip.002")), result(later))
    }

    /**
     * java.util.zip으로 폴더 항목 없이 [entries]를 담은 ZIP을 만든다.
     * @param name 파일 이름
     * @param entries 항목 경로와 내용
     * @return 만든 파일
     */
    private fun javaZip(name: String, vararg entries: Pair<String, String>): File =
        File(dir, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                for ((path, content) in entries) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(content.toByteArray())
                    zos.closeEntry()
                }
            }
        }

    /** [zip]을 묻지 않고 읽은 결과. */
    private fun result(zip: File): IoResult<ArchiveFolder> = runBlocking { repository.contentsOf(zip.path, UnexpectedPasswordProvider) }

    /** [zip]을 읽은 루트 폴더. 실패하면 테스트가 실패한다. */
    private fun contentsOf(zip: File): ArchiveFolder = when (val result = result(zip)) {
        is IoResult.Success -> result.value
        is IoResult.Failure -> throw AssertionError("목록을 읽지 못함: $result")
    }
}
