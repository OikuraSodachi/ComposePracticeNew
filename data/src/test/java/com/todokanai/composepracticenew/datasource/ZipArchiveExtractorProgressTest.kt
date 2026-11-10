package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.TransferTotal
import com.todokanai.composepracticenew.model.ProgressState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** [ZipArchiveExtractor]가 항목을 다 쓸 때마다 트래커에 완료를 알려, 크기를 모르는 항목이 섞인 작업에서도 크기를 아는 부분의 진행이 쌓이는지 검증하는 단위 테스트(#312). */
class ZipArchiveExtractorProgressTest {

    /** 아카이브와 압축 해제 결과를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("zip-extractor-progress-test").toFile()
    /** 보고된 진행 상태. */
    private val reports = mutableListOf<ProgressState>()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `크기를 아는 항목은 다 쓸 때마다 그 몫을 확정하고 다음 항목에서 되돌리지 않는다`() {
        val zip = zipOf("a.txt" to "aaaa", "b.txt" to "bb")
        val tracker = ByteProgressTracker(TransferTotal.Partial(knownBytes = 6, unknownFileCount = 1), totalFileCount = 3, onProgress = { reports += it })

        ZipArchiveExtractor().extract(zip.path, ExtractionRoot(File(dir, "out")), tracker)

        val known = reports.mapNotNull { it.knownSize }
        assertEquals(known.map { it.knownWrittenBytes }.sorted(), known.map { it.knownWrittenBytes })
        assertEquals(6L, known.last().knownCompletedBytes)
    }

    /** [entries](이름 → 내용)를 담은 ZIP 파일을 만든다. */
    private fun zipOf(vararg entries: Pair<String, String>): File =
        File(dir, "test.zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                entries.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray())
                    zos.closeEntry()
                }
            }
        }
}
