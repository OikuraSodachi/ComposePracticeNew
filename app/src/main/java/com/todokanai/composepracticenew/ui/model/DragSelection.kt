package com.todokanai.composepracticenew.ui.model

/**
 * 드래그 한 번의 범위 선택 규칙. 드래그를 시작할 때의 선택 위에 지금 범위를 더하거나 빼므로, 범위가 줄면 빠진 항목은 시작 때 상태로 돌아간다.
 * 항목은 화면의 선택 표시와 같이 경로로 판단한다. 시작 때의 선택은 항목이 아니라 경로로만 들고, 보고할 때마다 지금 목록에서 항목을 찾는다 —
 * 드래그 중 목록이 다시 조회돼 항목이 사라지거나 바뀌어도 시작 때의 항목을 선택에 되돌려 넣지 않도록(#524).
 *
 * @param basePaths 드래그를 시작할 때 선택돼 있던 항목의 경로
 * @param select true면 범위를 선택에 더하고, false면 범위를 선택에서 뺀다
 */
class DragSelection private constructor(
    private val basePaths: Set<String>,
    private val select: Boolean
) {
    /**
     * 지금 드래그 범위를 반영한 선택 목록.
     * @param range 드래그를 시작한 항목부터 지금 손가락 아래 항목까지
     * @param listed 지금 목록 — 시작 때 선택 중 범위 밖 항목을 이 목록에서 찾는다
     * @return 시작 때 선택 중 지금 목록에 있는 범위 밖 항목은 그대로 두고, 범위는 [select]에 따라 더하거나 뺀 목록
     */
    fun selectionWith(range: List<FileHolderItem>, listed: List<FileHolderItem>): List<FileHolderItem> {
        val rangePaths = range.mapTo(HashSet()) { it.path }
        val outsideRange = listed.filter { it.path in basePaths && it.path !in rangePaths }
        return if (select) outsideRange + range else outsideRange
    }

    companion object {
        /** 선택이 없는 상태(기본 모드)에서 시작한 드래그 — 범위를 선택한다. */
        val Fresh = DragSelection(emptySet(), select = true)

        /**
         * 다중 선택 중 [anchor]에서 시작한 드래그. [anchor]가 선택돼 있었으면 범위를 해제하고, 아니면 범위를 선택한다.
         * @param anchor 드래그를 시작한 항목
         * @param selected 드래그를 시작할 때의 선택 목록
         */
        fun startingAt(anchor: FileHolderItem, selected: List<FileHolderItem>): DragSelection {
            val paths = selected.mapTo(HashSet()) { it.path }
            return DragSelection(paths, select = anchor.path !in paths)
        }
    }
}
