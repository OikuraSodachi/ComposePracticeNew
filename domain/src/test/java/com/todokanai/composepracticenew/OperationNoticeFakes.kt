package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.kind
import com.todokanai.composepracticenew.repository.OperationNoticeReporter

/** 넣은 실패·완료 메시지를 종류별로 기록하는 테스트용 메시지 저장소. */
internal class RecordingOperationNotices : OperationNoticeReporter {
    /** 넣은 실패(거부 포함) 메시지(넣은 순서). */
    val failures = mutableListOf<NoticeMessage>()

    /** 넣은 완료 메시지(넣은 순서). */
    val completions = mutableListOf<NoticeMessage>()

    override fun report(message: NoticeMessage) {
        when (message.kind) {
            OperationNoticeKind.FAILURE -> failures += message
            OperationNoticeKind.COMPLETION -> completions += message
        }
    }
}
