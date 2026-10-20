package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** [SelectionSummary.from]이 선택 목록의 개수와 총 크기 표시 문자열을 기존 InfoDialog 계산과 같게 만드는지 검증하는 단위 테스트. */
class SelectionSummaryTest {

    @Test
    fun `개수와 크기 합의 표시 문자열을 계산한다`() {
        val items = listOf(item(sizeBytes = 1_000), item(sizeBytes = 2_048), item(sizeBytes = 0))

        val summary = SelectionSummary.from(items)

        assertEquals(3, summary.count)
        assertEquals(readableFileSize_td(3_048), summary.totalSizeText)
    }

    @Test
    fun `빈 목록은 0개와 0 크기로 요약한다`() {
        val summary = SelectionSummary.from(emptyList())

        assertEquals(0, summary.count)
        assertEquals(readableFileSize_td(0), summary.totalSizeText)
    }

    private fun item(sizeBytes: Long) = FileHolderItem(
        path = "/f$sizeBytes", isDirectory = false, name = "f$sizeBytes", size = "", lastModified = "", sizeBytes = sizeBytes
    )
}
