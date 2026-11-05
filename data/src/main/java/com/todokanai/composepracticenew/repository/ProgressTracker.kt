package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** 동시에 진행 중인 파일 작업들의 progress 상태를 instanceId별로 관리한다. */
@Singleton
class ProgressTracker @Inject constructor() : ProgressRepository {
    private val _progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
    override val progressMap: StateFlow<Map<Int, ProgressState>> = _progressMap.asStateFlow()

    override fun setProgressState(instanceId: Int, state: ProgressState) {
        _progressMap.update { it + (instanceId to state) }
    }

    override fun removeProgress(instanceId: Int) {
        _progressMap.update { it - instanceId }
    }
}
