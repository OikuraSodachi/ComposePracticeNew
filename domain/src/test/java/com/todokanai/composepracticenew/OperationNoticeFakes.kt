package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.OperationNotice
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.repository.OperationNoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 넣은 실패·완료 메시지를 종류별로 기록하는 테스트용 메시지 저장소. */
internal class RecordingOperationNotices : OperationNoticeRepository {
    /** 넣은 실패(거부 포함) 메시지(넣은 순서). */
    val failures = mutableListOf<String>()

    /** 넣은 완료 메시지(넣은 순서). */
    val completions = mutableListOf<String>()

    override val pending: StateFlow<List<OperationNotice>> = MutableStateFlow(emptyList())

    override fun report(kind: OperationNoticeKind, message: String) {
        when (kind) {
            OperationNoticeKind.FAILURE -> failures += message
            OperationNoticeKind.COMPLETION -> completions += message
        }
    }

    override fun claim(id: Long): Boolean = false
}
