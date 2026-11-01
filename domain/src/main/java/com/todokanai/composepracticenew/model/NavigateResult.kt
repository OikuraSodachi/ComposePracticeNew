package com.todokanai.composepracticenew.model

/**
 * 경로 이동 요청의 결과. 거부된 이동을 이동한 것으로 보지 않도록, 이동 뒤에 할 일(검색 닫기 등)은 [Navigated]일 때만 한다.
 */
enum class NavigateResult {
    /** 현재 경로가 요청한 경로로 바뀌었다. */
    Navigated,

    /** 구현체가 이동을 거부했다(읽을 수 없는 폴더, 다룰 수 없는 경로 등). 현재 경로는 그대로다. */
    Rejected
}
