package com.todokanai.composepracticenew.variables

/** 정렬 모드 문자열 목록과 각 항목 선택 콜백을 제공하는 인터페이스. */
interface FileListSorter {
    /**
     * 사용 가능한 정렬 모드 목록을 (표시 이름, 선택 콜백) 쌍 리스트로 반환한다.
     * @param callback 정렬 모드 문자열을 받아 저장·적용하는 콜백
     * @return (정렬 모드 표시 이름, 선택 시 콜백) 쌍으로 구성된 리스트
     */
    fun getSortModeCallbackList(callback: (String) -> Unit): List<Pair<String, () -> Unit>>
}
