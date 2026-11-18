package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.NoticeMessage

/** 파일 작업의 실패·거부·완료 메시지를 화면이 알릴 때까지 맡긴다. 맡긴 메시지를 꺼내는 쪽 계약은 [OperationNoticeRepository]다. */
interface OperationNoticeReporter {
    /**
     * 메시지를 맡긴다. 호출 스레드에서 바로 반환한다. 메시지의 종류는 내용으로 정해진다([com.todokanai.composepracticenew.model.kind]).
     * @param message 알릴 내용. 표시 문구는 화면이 정한다
     */
    fun report(message: NoticeMessage)
}
