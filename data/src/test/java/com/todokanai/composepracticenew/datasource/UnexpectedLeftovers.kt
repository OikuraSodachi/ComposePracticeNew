package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.repository.OperationNoticeReporter

/** 남은 파일을 지우지 못했다고 알리면 테스트를 실패시키는 정리 부품. 남은 파일을 언제나 지울 수 있는 테스트가 쓴다. */
internal val UnexpectedLeftoverDiscarder = LeftoverDiscarder(object : OperationNoticeReporter {
    override fun report(message: NoticeMessage) = throw AssertionError("예상하지 못한 정리 실패 알림: $message")
})
