package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNoticeKind

/** 파일 작업의 실패·거부·완료 메시지를 화면이 알릴 때까지 맡긴다. 맡긴 메시지를 꺼내는 쪽 계약은 [OperationNoticeRepository]다. */
interface OperationNoticeReporter {
    /**
     * 메시지를 맡긴다. 호출 스레드에서 바로 반환한다.
     * @param kind 메시지의 종류
     * @param message 사용자에게 보일 문구
     */
    fun report(kind: OperationNoticeKind, message: String)
}
