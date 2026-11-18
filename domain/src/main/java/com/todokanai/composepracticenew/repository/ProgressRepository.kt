package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.StateFlow

/**
 * 진행 중인 파일 작업의 progress 상태를 instanceId별로 읽는 쪽의 계약. 상태를 쓰는 쪽 계약은 [ProgressReporter]다.
 * 작업의 실패·완료 메시지는 [OperationNoticeRepository]가 맡는다.
 */
interface ProgressRepository {
    /** 진행 중인 모든 작업의 진행 상태. 키는 작업 인스턴스 ID다. */
    val progressMap: StateFlow<Map<Int, ProgressState>>
}
