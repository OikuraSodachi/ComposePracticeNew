package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.StateFlow

/**
 * 진행 중인 파일 작업의 progress 상태를 instanceId별로 관리하는 Repository 인터페이스.
 * 작업의 실패·완료 메시지는 [OperationNoticeRepository]가 맡는다.
 */
interface ProgressRepository {
    val progressMap: StateFlow<Map<Int, ProgressState>>
    fun setProgressState(instanceId: Int, state: ProgressState)
    fun removeProgress(instanceId: Int)
}
