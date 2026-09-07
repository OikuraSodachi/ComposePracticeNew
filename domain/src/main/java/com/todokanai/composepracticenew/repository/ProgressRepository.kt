package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 진행 중인 파일 작업의 progress 상태를 instanceId별로 관리하는 Repository 인터페이스. */
interface ProgressRepository {
    val progressMap: StateFlow<Map<Int, ProgressState>>
    /** 파일 작업 실패 시 발생하는 오류 메시지 이벤트. */
    val operationErrors: SharedFlow<String>
    /** 파일 작업 완료 시 발생하는 완료 메시지 이벤트. */
    val operationCompletions: SharedFlow<String>
    fun setProgressState(instanceId: Int, state: ProgressState)
    fun removeProgress(instanceId: Int)
    fun emitError(message: String)
    fun emitCompletion(message: String)
}
