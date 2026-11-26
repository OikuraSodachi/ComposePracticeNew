package com.todokanai.composepracticenew.ui.model

/**
 * 목록 항목을 눌렀을 때 할 일. 선택 모드별 규칙([LocalSelectMode.clickActionOf], [RemoteSelectMode.clickActionOf])이 정하고,
 * 목록 행과 검색 결과가 exhaustive `when`으로 실행한다(#455).
 */
enum class ItemClickAction {
    /** 선택 목록에 넣거나 뺀다. */
    TOGGLE,

    /** 연다 — 폴더는 그리로 이동하고, 파일은 외부 앱으로 연다. */
    OPEN,

    /** 아무것도 하지 않는다. */
    IGNORE,
}
