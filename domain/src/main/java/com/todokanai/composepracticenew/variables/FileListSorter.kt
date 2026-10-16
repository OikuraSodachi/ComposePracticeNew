package com.todokanai.composepracticenew.variables

/** 정렬 모드 문자열 목록과 각 항목 선택 콜백을 제공하는 인터페이스. */
interface FileListSorter {
    fun getSortModeCallbackList(callback: (String) -> Unit): List<Pair<String, () -> Unit>>
}
