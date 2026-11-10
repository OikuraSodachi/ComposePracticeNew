package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [LocalArchiveExtraction]이 배치 방식마다 목적지 바로 아래에 생길 이름을 해제와 같은 규칙으로 구하는지 검증하는 단위 테스트(#242).
 * 충돌 판정이 이 이름으로 하므로, 해제가 목적지에 만든 이름이 이 집합을 벗어나면 확인 없이 덮어쓰는 항목이 생긴다.
 */
class LocalArchiveExtractionLayoutTest {

    /** 아카이브와 목적지를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("archive-layout-test").toFile()
    /** 압축 해제 목적지. */
    private val dest: File = File(dir, "dest").apply { mkdirs() }
    /** 검증 대상. ZIP 추출기만 둔다. */
    private val extraction = LocalArchiveExtraction(setOf(ZipArchiveExtractor()))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `목적지에 바로 풀면 항목 경로를 정규화한 최상위 이름을 돌려주고 루트 밖 항목은 뺀다`() {
        val zip = zipOf("a.zip", "readme.txt", "docs/", "docs/a.txt", "./b.txt", "../evil.txt")

        assertEquals(IoResult.Success(setOf("readme.txt", "docs", "b.txt")), names(zip, ExtractLayout.IntoDestination))
    }

    @Test
    fun `아카이브 폴더에 풀면 확장자를 뗀 아카이브 이름 하나를 돌려준다`() {
        assertEquals(IoResult.Success(setOf("a")), names(zipOf("a.zip", "readme.txt"), ExtractLayout.IntoArchiveFolder))
    }

    @Test
    fun `숨김 표시 점으로 시작하는 아카이브는 이름 전체를 폴더 이름으로 써 목적지에 바로 풀리지 않는다`() {
        val zip = zipOf(".zip", "readme.txt")

        assertEquals(IoResult.Success(setOf(".zip")), names(zip, ExtractLayout.IntoArchiveFolder))
    }

    @Test
    fun `목적지에 바로 풀어 생긴 이름은 미리 구한 이름과 같다`() {
        val zip = zipOf("a.zip", "readme.txt", "docs/a.txt", "./b.txt", "empty/")
        val expected = names(zip, ExtractLayout.IntoDestination)

        runBlocking { extraction.unzip(setOf(zip.path), dest.path, ExtractLayout.IntoDestination) {} }

        assertEquals(expected, IoResult.Success(dest.list()!!.toSet()))
    }

    @Test
    fun `아카이브 폴더에 풀어 생긴 이름은 미리 구한 이름과 같다`() {
        val zip = zipOf("a.zip", "readme.txt")
        val expected = names(zip, ExtractLayout.IntoArchiveFolder)

        runBlocking { extraction.unzip(setOf(zip.path), dest.path, ExtractLayout.IntoArchiveFolder) {} }

        assertEquals(expected, IoResult.Success(dest.list()!!.toSet()))
    }

    @Test
    fun `지원하지 않는 형식이면 이름을 구하지 않고 실패한다`() {
        val other = File(dir, "a.7z").apply { writeText("x") }

        assertTrue(names(other, ExtractLayout.IntoDestination) is IoResult.ItemFailed)
    }

    /** [zip]을 [dest]에 [layout]으로 풀 때 생길 이름을 구한다. */
    private fun names(zip: File, layout: ExtractLayout): IoResult<Set<String>> = runBlocking { extraction.extractedNames(zip.path, dest.path, layout) }

    /** [entryNames]를 담은(폴더 항목은 `/`로 끝남) [name] ZIP 파일을 목적지 밖에 만든다. */
    private fun zipOf(name: String, vararg entryNames: String): File =
        File(dir, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                entryNames.forEach { entryName ->
                    zos.putNextEntry(ZipEntry(entryName))
                    if (!entryName.endsWith("/")) zos.write("x".toByteArray())
                    zos.closeEntry()
                }
            }
        }
}
