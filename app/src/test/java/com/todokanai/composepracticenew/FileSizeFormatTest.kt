package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * readableFileSize_td가 Long의 값 범위 전체에서 단위를 붙여 표시하는지 검증하는 단위 테스트.
 */
class FileSizeFormatTest {

    /** 테스트 전의 기본 Locale. 테스트가 끝나면 되돌린다. */
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `PiB 범위의 크기는 PB로 표시한다`() {
        assertEquals("1.5 PB", readableFileSize_td(1024L * 1024 * 1024 * 1024 * 1024 * 3 / 2))
    }

    @Test
    fun `Long의 최대값은 EB로 표시한다`() {
        assertEquals("8 EB", readableFileSize_td(Long.MAX_VALUE))
    }
}
