package com.todokanai.composepracticenew.model

/**
 * 화면에 알릴 파일 작업 메시지 하나.
 * @param message 알릴 내용. 표시 문구는 화면이 정한다
 */
data class OperationNotice(val message: NoticeMessage) {
    /** 메시지의 종류. 화면은 종류에 따라 보여 주는 시간을 정한다. 내용으로 정해지므로([NoticeMessage.kind]) 따로 받지 않는다. */
    val kind: OperationNoticeKind get() = message.kind
}

/** 파일 작업 메시지의 종류. */
enum class OperationNoticeKind {
    /** 작업이 실패했거나, 실행 전에 거부됐다. */
    FAILURE,

    /** 작업이 끝났다. */
    COMPLETION
}
