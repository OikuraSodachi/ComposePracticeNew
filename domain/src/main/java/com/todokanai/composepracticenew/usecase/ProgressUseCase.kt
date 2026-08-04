package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.flow.StateFlow

/** 진행 중인 파일 작업의 progress 상태를 읽고 업데이트하는 UseCase. */
class ProgressUseCase(private val repo: ProgressRepository) {
    val progressMap: StateFlow<Map<Int, ProgressState>> = repo.progressMap
    fun setProgressState(actionKey: Int, state: ProgressState) = repo.setProgressState(actionKey, state)
    fun removeProgress(actionKey: Int) = repo.removeProgress(actionKey)
}
