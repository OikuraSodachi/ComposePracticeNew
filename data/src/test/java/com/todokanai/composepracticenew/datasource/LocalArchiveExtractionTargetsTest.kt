package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ExtractLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** [LocalArchiveExtraction.extractionTargetOf]가 주입된 추출기의 이름 규칙으로 경로를 풀 아카이브로 묶는지 검증하는 단위 테스트. */
class LocalArchiveExtractionTargetsTest {

    /** 앱에 등록되는 것과 같은 추출기 집합을 쓰는 압축 해제 조율기. */
    private val extraction = LocalArchiveExtraction(
        setOf(ZipArchiveExtractor(), SevenZipArchiveExtractor.rar(), SevenZipArchiveExtractor.sevenZip(), SevenZipArchiveExtractor.iso()),
        UnexpectedLeftoverDiscarder,
    )

    @Test
    fun `등록된 추출기가 지원하는 형식은 대소문자와 무관하게 풀 수 있다`() {
        listOf("/sdcard/backup.zip", "/sdcard/Backup.ZIP", "/sdcard/photos.rar", "/sdcard/data.7z", "/sdcard/ubuntu.iso", "/sdcard/Win11.ISO").forEach {
            assertEquals(it, it, extraction.extractionTargetOf(setOf(it)))
        }
    }

    @Test
    fun `추출기가 없는 형식은 풀 수 없다`() {
        assertNull(extraction.extractionTargetOf(setOf("/sdcard/notes.txt")))
        assertNull(extraction.extractionTargetOf(setOf("/sdcard/a.zip", "/sdcard/notes.txt")))
    }

    @Test
    fun `분할 압축의 시작 볼륨은 혼자 풀 수 있다`() {
        listOf("/d/a.part1.rar", "/d/a.part01.rar", "/d/a.7z.001", "/d/a.zip.001").forEach {
            assertEquals(it, it, extraction.extractionTargetOf(setOf(it)))
        }
    }

    @Test
    fun `분할 압축의 다른 볼륨은 혼자 풀 수 없다`() {
        listOf("/d/a.part2.rar", "/d/a.r00", "/d/a.z01", "/d/a.7z.002", "/d/a.zip.002").forEach {
            assertNull(it, extraction.extractionTargetOf(setOf(it)))
        }
    }

    @Test
    fun `시작 볼륨과 같은 세트의 다른 볼륨은 시작 볼륨 하나로 묶는다`() {
        assertEquals("/d/a.part1.rar", extraction.extractionTargetOf(setOf("/d/a.part2.rar", "/d/a.part1.rar", "/d/a.part3.rar")))
        assertEquals("/d/a.rar", extraction.extractionTargetOf(setOf("/d/a.rar", "/d/a.r00", "/d/a.r01")))
        assertEquals("/d/a.zip", extraction.extractionTargetOf(setOf("/d/a.z01", "/d/a.zip")))
        assertEquals("/d/a.7z.001", extraction.extractionTargetOf(setOf("/d/a.7z.001", "/d/a.7z.002")))
        assertEquals("/d/a.zip.001", extraction.extractionTargetOf(setOf("/d/a.zip.001", "/d/a.zip.002")))
    }

    @Test
    fun `시작 볼륨이 없는 다른 볼륨들은 풀 수 없다`() {
        assertNull(extraction.extractionTargetOf(setOf("/d/a.part2.rar", "/d/a.part3.rar")))
    }

    @Test
    fun `다른 세트의 시작 볼륨은 다른 볼륨을 묶지 않는다`() {
        assertNull(extraction.extractionTargetOf(setOf("/d/a.part1.rar", "/d/b.part2.rar")))
        // 같은 이름이라도 분할 방식이 다르면 다른 세트다
        assertNull(extraction.extractionTargetOf(setOf("/d/a.zip", "/d/a.zip.002")))
        assertNull(extraction.extractionTargetOf(setOf("/d/a.rar", "/d/a.part2.rar")))
    }

    @Test
    fun `다른 폴더의 시작 볼륨은 다른 볼륨을 묶지 않는다`() {
        assertNull(extraction.extractionTargetOf(setOf("/d/a.part1.rar", "/e/a.part2.rar")))
    }

    @Test
    fun `아카이브 둘 이상으로 묶이는 경로는 풀 대상이 없다`() {
        assertNull(extraction.extractionTargetOf(linkedSetOf("/d/b.zip", "/d/a.part1.rar", "/d/a.part2.rar")))
        assertNull(extraction.extractionTargetOf(setOf("/d/a.zip", "/d/b.zip")))
    }

    @Test
    fun `아카이브 둘 이상으로 묶이는 경로는 아무것도 풀지 않고 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { extraction.unzip(setOf("/d/a.zip", "/d/b.zip"), "/d/dest", ExtractLayout.IntoArchiveFolder, UnexpectedConflictResolver, UnexpectedPasswordProvider) {} }
        }
    }
}
