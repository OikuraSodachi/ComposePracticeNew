package com.todokanai.composepracticenew.ui.model

/**
 * 드래그 한 번의 범위 선택 규칙. 드래그를 시작할 때의 선택 위에 지금 범위를 더하거나 빼므로, 범위가 줄면 빠진 항목은 시작 때 상태로 돌아간다.
 * 항목은 화면의 선택 표시와 같이 경로로 판단한다.
 *
 * @param base 드래그를 시작할 때의 선택 목록
 * @param select true면 범위를 선택에 더하고, false면 범위를 선택에서 뺀다
 */
class DragSelection private constructor(
    private val base: List<FileHolderItem>,
    private val select: Boolean
) {
    /**
     * 지금 드래그 범위를 반영한 선택 목록.
     * @param range 드래그를 시작한 항목부터 지금 손가락 아래 항목까지
     * @return 시작 때 선택에서 범위 밖은 그대로 두고, 범위는 [select]에 따라 더하거나 뺀 목록
     */
    fun selectionWith(range: List<FileHolderItem>): List<FileHolderItem> {
        val rangePaths = range.mapTo(HashSet()) { it.path }
        val outsideRange = base.filterNot { it.path in rangePaths }
        return if (select) outsideRange + range else outsideRange
    }

    companion object {
        /** 선택이 없는 상태(기본 모드)에서 시작한 드래그 — 범위를 선택한다. */
        val Fresh = DragSelection(emptyList(), select = true)

        /**
         * 다중 선택 중 [anchor]에서 시작한 드래그. [anchor]가 선택돼 있었으면 범위를 해제하고, 아니면 범위를 선택한다.
         * @param anchor 드래그를 시작한 항목
         * @param selected 드래그를 시작할 때의 선택 목록
         */
        fun startingAt(anchor: FileHolderItem, selected: List<FileHolderItem>) =
            DragSelection(selected, select = selected.none { it.path == anchor.path })
    }
}
