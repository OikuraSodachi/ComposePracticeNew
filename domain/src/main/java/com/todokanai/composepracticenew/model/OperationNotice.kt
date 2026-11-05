package com.todokanai.composepracticenew.model

/**
 * 화면에 알리기를 기다리는 파일 작업 메시지 하나.
 * @param id 알림마다 다른 식별자. 화면이 이 알림을 꺼낼 때([com.todokanai.composepracticenew.repository.OperationNoticeRepository.claim]) 쓴다
 * @param kind 메시지의 종류
 * @param message 사용자에게 보일 문구
 */
data class OperationNotice(val id: Long, val kind: OperationNoticeKind, val message: String)

/** 파일 작업 메시지의 종류. */
enum class OperationNoticeKind {
    /** 작업이 실패했거나, 실행 전에 거부됐다. */
    FAILURE,

    /** 작업이 끝났다. */
    COMPLETION
}
