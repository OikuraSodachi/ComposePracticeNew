package com.todokanai.composepracticenew.model

/**
 * 화면에 알릴 파일 작업 메시지 하나.
 * @param kind 메시지의 종류. 화면은 종류에 따라 보여 주는 시간을 정한다
 * @param message 사용자에게 보일 문구
 */
data class OperationNotice(val kind: OperationNoticeKind, val message: String)

/** 파일 작업 메시지의 종류. */
enum class OperationNoticeKind {
    /** 작업이 실패했거나, 실행 전에 거부됐다. */
    FAILURE,

    /** 작업이 끝났다. */
    COMPLETION
}
