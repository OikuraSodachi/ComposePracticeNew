package com.todokanai.composepracticenew.variables

import com.todokanai.composepracticenew.model.SortMode
import javax.inject.Inject

/** FileListSorter의 기본 구현체. [SortMode]의 선언 순서대로 선택 목록을 구성한다. */
class FileListSorterImpl @Inject constructor() : FileListSorter {

    override fun getSortModeCallbackList(callback: (SortMode) -> Unit): List<Pair<String, () -> Unit>> =
        SortMode.entries.map { mode -> mode.id to { callback(mode) } }
}
