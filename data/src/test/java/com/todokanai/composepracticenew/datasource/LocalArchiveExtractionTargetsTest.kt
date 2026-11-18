package com.todokanai.composepracticenew.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [LocalArchiveExtraction.extractionTargetsOf]가 주입된 추출기의 이름 규칙으로 경로를 풀 아카이브로 묶는지 검증하는 단위 테스트. */
class LocalArchiveExtractionTargetsTest {

    /** 앱에 등록되는 것과 같은 추출기 집합을 쓰는 압축 해제 조율기. */
    private val extraction = LocalArchiveExtraction(
        setOf(ZipArchiveExtractor(), SevenZipArchiveExtractor.rar(), SevenZipArchiveExtractor.sevenZip(), SevenZipArchiveExtractor.iso()),
    )

    @Test
    fun `등록된 추출기가 지원하는 형식은 대소문자와 무관하게 풀 수 있다`() {
        listOf("/sdcard/backup.zip", "/sdcard/Backup.ZIP", "/sdcard/photos.rar", "/sdcard/data.7z", "/sdcard/ubuntu.iso", "/sdcard/Win11.ISO").forEach {
            assertEquals(it, listOf(it), extraction.extractionTargetsOf(setOf(it)))
        }
    }

    @Test
    fun `추출기가 없는 형식은 풀 수 없다`() {
        assertNull(extraction.extractionTargetsOf(setOf("/sdcard/notes.txt")))
        assertNull(extraction.extractionTargetsOf(setOf("/sdcard/a.zip", "/sdcard/notes.txt")))
    }

    @Test
    fun `분할 압축의 시작 볼륨은 혼자 풀 수 있다`() {
        listOf("/d/a.part1.rar", "/d/a.part01.rar", "/d/a.7z.001", "/d/a.zip.001").forEach {
            assertEquals(it, listOf(it), extraction.extractionTargetsOf(setOf(it)))
        }
    }

    @Test
    fun `분할 압축의 다른 볼륨은 혼자 풀 수 없다`() {
        listOf("/d/a.part2.rar", "/d/a.r00", "/d/a.z01", "/d/a.7z.002", "/d/a.zip.002").forEach {
            assertNull(it, extraction.extractionTargetsOf(setOf(it)))
        }
    }

    @Test
    fun `시작 볼륨과 같은 세트의 다른 볼륨은 시작 볼륨 하나로 묶는다`() {
        assertEquals(listOf("/d/a.part1.rar"), extraction.extractionTargetsOf(setOf("/d/a.part2.rar", "/d/a.part1.rar", "/d/a.part3.rar")))
        assertEquals(listOf("/d/a.rar"), extraction.extractionTargetsOf(setOf("/d/a.rar", "/d/a.r00", "/d/a.r01")))
        assertEquals(listOf("/d/a.zip"), extraction.extractionTargetsOf(setOf("/d/a.z01", "/d/a.zip")))
        assertEquals(listOf("/d/a.7z.001"), extraction.extractionTargetsOf(setOf("/d/a.7z.001", "/d/a.7z.002")))
        assertEquals(listOf("/d/a.zip.001"), extraction.extractionTargetsOf(setOf("/d/a.zip.001", "/d/a.zip.002")))
    }

    @Test
    fun `시작 볼륨이 없는 다른 볼륨들은 풀 수 없다`() {
        assertNull(extraction.extractionTargetsOf(setOf("/d/a.part2.rar", "/d/a.part3.rar")))
    }

    @Test
    fun `다른 세트의 시작 볼륨은 다른 볼륨을 묶지 않는다`() {
        assertNull(extraction.extractionTargetsOf(setOf("/d/a.part1.rar", "/d/b.part2.rar")))
        // 같은 이름이라도 분할 방식이 다르면 다른 세트다
        assertNull(extraction.extractionTargetsOf(setOf("/d/a.zip", "/d/a.zip.002")))
        assertNull(extraction.extractionTargetsOf(setOf("/d/a.rar", "/d/a.part2.rar")))
    }

    @Test
    fun `다른 폴더의 시작 볼륨은 다른 볼륨을 묶지 않는다`() {
        assertNull(extraction.extractionTargetsOf(setOf("/d/a.part1.rar", "/e/a.part2.rar")))
    }

    @Test
    fun `여러 아카이브는 받은 순서대로 각각 푼다`() {
        assertEquals(
            listOf("/d/b.zip", "/d/a.part1.rar"),
            extraction.extractionTargetsOf(linkedSetOf("/d/b.zip", "/d/a.part1.rar", "/d/a.part2.rar")),
        )
    }
}
