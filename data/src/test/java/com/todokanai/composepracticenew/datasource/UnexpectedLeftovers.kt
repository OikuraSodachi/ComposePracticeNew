package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import java.io.IOException

/** 정리 실패를 알리면 테스트를 실패시키는 알림함. */
private val UnexpectedLeftoverReport = object : OperationNoticeReporter {
    override fun report(message: NoticeMessage) = throw AssertionError("예상하지 못한 정리 실패 알림: $message")
}

/** 남은 파일을 지우지 못했다고 알리면 테스트를 실패시키는 정리 부품. 남은 파일을 언제나 지울 수 있는 테스트가 쓴다. */
internal val UnexpectedLeftoverDiscarder = LeftoverDiscarder(UnexpectedLeftoverReport)

/** [UndeletableLeftoverDiscarder]가 지우려 할 때마다 내는 실패의 메시지. */
internal const val UNDELETABLE_MESSAGE = "지울 수 없음"

/** 어떤 파일도 지우지 못하는 정리 부품. 결과를 돌려주는 경로가 정리 실패를 실패 원인에 담는지 확인하는 테스트가 쓴다 — 알림함으로 보내면 테스트가 실패한다. */
internal val UndeletableLeftoverDiscarder = LeftoverDiscarder(UnexpectedLeftoverReport) { throw IOException(UNDELETABLE_MESSAGE) }
