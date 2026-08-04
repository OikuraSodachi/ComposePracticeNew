package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.todokanai.composepracticenew.repository.ProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 동시에 진행 중인 파일 작업들의 progress 상태를 actionKey별로 관리한다. */
@Singleton
class ProgressTracker @Inject constructor() : ProgressRepository {
    private val _progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
    val progressMap: StateFlow<Map<Int, ProgressState>> = _progressMap.asStateFlow()

    fun setProgressState(actionKey: Int, state: ProgressState) {
        _progressMap.update { it + (actionKey to state) }
    }

    fun removeProgress(actionKey: Int) {
        _progressMap.update { it - actionKey }
    }
}
