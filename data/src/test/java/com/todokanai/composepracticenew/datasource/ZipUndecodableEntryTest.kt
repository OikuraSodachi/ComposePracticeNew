package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

/**
 * 항목 이름을 해석하지 못하는 ZIP을 [ZipArchiveExtractor]가 IOException으로 알려, 충돌 검사와 압축 해제가 앱을 끝내지 않고 실패 결과로 끝나는지 검증하는 단위 테스트(#315 리뷰).
 * JVM의 java.util.zip은 그런 아카이브를 열 때 ZipException으로 알리므로, 구형(Android 13 이하)의 IAE 경로는 [decodingZipEntries]로 따로 검증한다.
 */
class ZipUndecodableEntryTest {

    /** 아카이브와 목적지를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("zip-undecodable-test").toFile()
    /** 압축 해제 목적지. */
    private val dest: File = File(dir, "dest").apply { mkdirs() }
    /** 검증 대상. ZIP 추출기만 둔다. */
    private val extraction = LocalArchiveExtraction(setOf(ZipArchiveExtractor()))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `항목을 읽다 이름 해석에 실패하면 원인을 담은 ZipException으로 알린다`() {
        val malformed = IllegalArgumentException("MALFORMED")

        val thrown = assertThrows(ZipException::class.java) { decodingZipEntries { throw malformed } }

        assertSame(malformed, thrown.cause)
    }

    @Test
    fun `UTF-8이 아닌 이름의 ZIP은 풀 이름을 구하지 않고 실패 결과로 돌려준다`() {
        assertTrue(runBlocking { extraction.extractedNames(cp949Zip().path, dest.path, ExtractLayout.IntoDestination) } is IoResult.ItemFailed)
    }

    @Test
    fun `UTF-8이 아닌 이름의 ZIP은 풀지 않고 실패 결과로 돌려준다`() {
        val result = runBlocking { extraction.unzip(setOf(cp949Zip().path), dest.path, ExtractLayout.IntoDestination) {} }

        assertTrue(result !is IoResult.Success)
        assertTrue(dest.list()!!.isEmpty())
    }

    /** UTF-8 표시 없이 CP949로 이름을 적은(Windows 탐색기로 만든 한글 이름 ZIP과 같은) ZIP 파일을 만든다. */
    private fun cp949Zip(): File =
        File(dir, "a.zip").also { file ->
            ZipOutputStream(file.outputStream(), Charset.forName("MS949")).use { zos ->
                zos.putNextEntry(ZipEntry("한글.txt"))
                zos.write("x".toByteArray())
                zos.closeEntry()
            }
        }
}
