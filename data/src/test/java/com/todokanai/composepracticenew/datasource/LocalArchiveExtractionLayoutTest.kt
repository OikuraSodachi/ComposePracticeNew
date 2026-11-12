package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.WriteConflictResolver
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
    fun `폴더 항목 자리에 같은 이름의 파일이 있으면 종류가 엇갈린 겹침으로 묻고 건너뛰면 그 파일을 그대로 둔다`() {
        val zip = zipOf("a.zip", "empty/", "empty/inner.txt")
        File(dest, "empty").writeText("file")
        val asked = mutableListOf<WriteConflict>()

        val result = runBlocking {
            extraction.unzip(setOf(zip.path), dest.path, ExtractLayout.IntoDestination, skipping(asked)) {}
        }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf(WriteConflictKind.TYPE_MISMATCH), asked.map { it.kind })
        assertEquals("file", File(dest, "empty").readText())
    }

    @Test
    fun `아카이브 폴더 자리에 같은 이름의 파일이 있으면 묻고 건너뛰면 그 아카이브를 풀지 않는다`() {
        val zip = zipOf("a.zip", "empty/")
        File(dest, "a").writeText("file")
        val asked = mutableListOf<WriteConflict>()

        val result = runBlocking {
            extraction.unzip(setOf(zip.path), dest.path, ExtractLayout.IntoArchiveFolder, skipping(asked)) {}
        }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf(WriteConflict("a", dest.canonicalPath, WriteConflictKind.TYPE_MISMATCH)), asked.map { it.copy(folderPath = File(it.folderPath).canonicalPath) })
        assertEquals("file", File(dest, "a").readText())
    }

    /** 받은 겹침을 [asked]에 기록하고 건너뛰는 resolver. */
    private fun skipping(asked: MutableList<WriteConflict>) = WriteConflictResolver { conflict ->
        asked += conflict
        ConflictChoice.SKIP
    }

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
