package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.OperationNotice as DomainOperationNotice

/**
 * 화면에 알릴 파일 작업 메시지 하나(표시용).
 * @param kind 메시지의 종류. 화면은 종류에 따라 보여 주는 시간을 정한다
 * @param message 사용자에게 보일 문구
 */
@Immutable
data class OperationNotice(val kind: OperationNoticeKind, val message: String) {
    companion object {
        /** domain [DomainOperationNotice]를 표시용 메시지로 바꾼다. */
        fun from(notice: DomainOperationNotice) = OperationNotice(notice.kind, notice.message)
    }
}
