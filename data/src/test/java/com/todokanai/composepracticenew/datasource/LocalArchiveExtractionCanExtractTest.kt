package com.todokanai.composepracticenew.datasource

import org.junit.Assert.assertEquals
import org.junit.Test

/** [LocalArchiveExtraction.canExtract]가 주입된 추출기의 지원 형식만 따르는지 검증하는 단위 테스트. */
class LocalArchiveExtractionCanExtractTest {

    /** 앱에 등록되는 것과 같은 추출기 집합을 쓰는 압축 해제 조율기. */
    private val extraction = LocalArchiveExtraction(setOf(ZipArchiveExtractor(), RarArchiveExtractor()))

    @Test
    fun `등록된 추출기가 지원하는 형식은 대소문자와 무관하게 풀 수 있다`() {
        assertEquals(true, extraction.canExtract("/sdcard/backup.zip"))
        assertEquals(true, extraction.canExtract("/sdcard/Backup.ZIP"))
        assertEquals(true, extraction.canExtract("/sdcard/photos.rar"))
    }

    @Test
    fun `추출기가 없는 형식은 풀 수 없다`() {
        assertEquals(false, extraction.canExtract("/sdcard/data.7z"))
        assertEquals(false, extraction.canExtract("/sdcard/notes.txt"))
    }
}
