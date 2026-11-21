package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/** [LocalArchiveExtraction]이 분할 ZIP(PKWARE `.z01` 방식, 번호 `.zip.001` 방식)을 시작 파일로 풀고 빠진 볼륨을 알리는지 zip4j로 검증하는 통합 테스트. */
class SplitZipExtractionTest {

    /** 아카이브를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("split-zip-test").toFile()
    /** 압축 해제 목적지. */
    private val dest: File = File(dir, "dest").apply { mkdirs() }
    /** 압축할 내용. 볼륨 여러 개에 걸치도록 분할 최소 크기(64KB)의 세 배를 넘게 둔다. */
    private val content: ByteArray = Random(1).nextBytes(SPLIT_SIZE * 3 + 1000)
    /** 검증 대상. ZIP 추출기만 둔다. */
    private val extraction = LocalArchiveExtraction(setOf(ZipArchiveExtractor()), UnexpectedLeftoverDiscarder)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `PKWARE 분할 ZIP은 zip 파일로 열어 아카이브 이름의 폴더에 푼다`() {
        val zip = pkwareSplitZip()

        val result = unzip(zip)

        assertEquals(IoResult.Done, result)
        assertArrayEquals(content, File(dest, "a/big.bin").readBytes())
    }

    @Test
    fun `PKWARE 분할 ZIP의 볼륨을 함께 받아도 한 번만 푼다`() {
        val zip = pkwareSplitZip()
        val volumes = dir.listFiles()!!.filter { it.isFile }.map { it.path }

        val result = runBlocking { extraction.unzip(volumes.toSet(), dest.path, ExtractLayout.IntoArchiveFolder, UnexpectedConflictResolver, UnexpectedPasswordProvider) {} }

        assertTrue(volumes.toString(), volumes.size > 2)
        assertEquals(IoResult.Done, result)
        assertArrayEquals(content, File(dest, "a/big.bin").readBytes())
        assertEquals(listOf("a"), dest.list()!!.toList())
        assertEquals(zip.path, extraction.extractionTargetOf(volumes.toSet()))
    }

    @Test
    fun `PKWARE 분할 ZIP의 볼륨이 빠지면 아무것도 풀지 않고 빠진 볼륨을 알린다`() {
        val zip = pkwareSplitZip()
        File(dir, "a.z02").delete()

        val result = unzip(zip)

        assertEquals(IoResult.ItemFailed(FailureReason.MissingArchiveVolume("a.zip", "a.z02")), result)
        assertFalse(File(dest, "a").exists())
    }

    @Test
    fun `번호 분할 ZIP은 001 볼륨으로 열어 볼륨 번호와 확장자를 뺀 이름의 폴더에 푼다`() {
        val first = numberedSplitZip()

        val result = unzip(first)

        assertEquals(IoResult.Done, result)
        assertArrayEquals(content, File(dest, "a/big.bin").readBytes())
    }

    @Test
    fun `번호 분할 ZIP의 가운데 볼륨이 빠지면 아무것도 풀지 않고 빠진 볼륨을 알린다`() {
        val first = numberedSplitZip()
        File(dir, "a.zip.002").delete()

        val result = unzip(first)

        assertEquals(IoResult.ItemFailed(FailureReason.MissingArchiveVolume("a.zip.001", "a.zip.002")), result)
        assertFalse(File(dest, "a").exists())
    }

    /**
     * [archive]를 아카이브 폴더에 푼다.
     * @param archive 시작 파일
     * @return 압축 해제 결과
     */
    private fun unzip(archive: File): IoResult<Unit> = runBlocking {
        extraction.unzip(setOf(archive.path), dest.path, ExtractLayout.IntoArchiveFolder, UnexpectedConflictResolver, UnexpectedPasswordProvider) {}
    }

    /**
     * [content]를 `big.bin`으로 담은 PKWARE 분할 ZIP(`a.z01`, `a.z02`, `a.z03`, `a.zip`)을 zip4j로 만든다.
     * @return 내용 목록이 있는 `a.zip`
     */
    private fun pkwareSplitZip(): File {
        val source = sourceFile()
        val zip = File(dir, "a.zip")
        ZipFile(zip).use { it.createSplitZipFile(listOf(source), storeParameters(), true, SPLIT_SIZE.toLong()) }
        source.delete()
        return zip
    }

    /**
     * [content]를 `big.bin`으로 담은 ZIP을 만들어 [SPLIT_SIZE]씩 잘라 번호 볼륨(`a.zip.001` …)으로 둔다.
     * @return `a.zip.001`
     */
    private fun numberedSplitZip(): File {
        val source = sourceFile()
        val whole = File(dir, "whole.tmp")
        ZipFile(whole).use { it.addFile(source, storeParameters()) }
        whole.readBytes().asList().chunked(SPLIT_SIZE).forEachIndexed { i, chunk ->
            File(dir, "a.zip.%03d".format(i + 1)).writeBytes(chunk.toByteArray())
        }
        whole.delete()
        source.delete()
        return File(dir, "a.zip.001")
    }

    /** [content]를 담은 `big.bin`을 아카이브 디렉터리 밖에 만든다. */
    private fun sourceFile(): File = File(Files.createTempDirectory(dir.toPath(), "src").toFile(), "big.bin").apply { writeBytes(content) }

    /** 내용이 그대로 볼륨에 나뉘도록 압축하지 않고 담는 매개변수. */
    private fun storeParameters() = ZipParameters().apply { compressionMethod = CompressionMethod.STORE }

    private companion object {
        /** 볼륨 크기. zip4j 분할 최소 크기(64KB)다. */
        const val SPLIT_SIZE = 65536
    }
}
