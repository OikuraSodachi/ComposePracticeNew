package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * 선택 항목의 요약(개수와 총 크기 표시 문자열). 정보 다이얼로그에 표시한다.
 * @param count 선택 항목 수
 * @param totalSizeText 선택 항목 크기 합을 사람이 읽기 쉬운 단위로 포맷한 문자열
 */
data class SelectionSummary(
    val count: Int,
    val totalSizeText: String
) {
    companion object {
        /** 선택 목록으로부터 개수와 총 크기 표시 문자열을 계산해 요약을 만든다. */
        fun from(items: List<FileHolderItem>) = SelectionSummary(
            count = items.size,
            totalSizeText = readableFileSize_td(items.sumOf { it.sizeBytes })
        )
    }
}
