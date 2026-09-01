package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

/** 진행 중인 파일 작업의 progress 상태를 읽고 업데이트하며, 작업 실패 이벤트를 중계하는 UseCase. */
class ProgressUseCase(private val repo: ProgressRepository) {
    private val instanceCounter = AtomicInteger(0)

    val progressMap: StateFlow<Map<Int, ProgressState>> = repo.progressMap
    /** 파일 작업 실패 시 발생하는 오류 메시지 이벤트. */
    val operationErrors: SharedFlow<String> = repo.operationErrors
    /** 파일 작업 완료 시 발생하는 완료 메시지 이벤트. */
    val operationCompletions: SharedFlow<String> = repo.operationCompletions

    /** 작업 인스턴스마다 전역적으로 고유한 ID를 발급한다. */
    fun nextInstanceId(): Int = instanceCounter.incrementAndGet()
    fun setProgressState(actionKey: Int, state: ProgressState) = repo.setProgressState(actionKey, state)
    fun removeProgress(actionKey: Int) = repo.removeProgress(actionKey)
    fun emitError(message: String) = repo.emitError(message)
    fun emitCompletion(message: String) = repo.emitCompletion(message)
}
