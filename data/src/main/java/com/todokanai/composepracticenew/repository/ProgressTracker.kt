package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.todokanai.composepracticenew.repository.ProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 동시에 진행 중인 파일 작업들의 progress 상태를 instanceId별로 관리한다. */
@Singleton
class ProgressTracker @Inject constructor() : ProgressRepository {
    private val _progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
    override val progressMap: StateFlow<Map<Int, ProgressState>> = _progressMap.asStateFlow()

    private val _operationErrors = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val operationErrors: SharedFlow<String> = _operationErrors.asSharedFlow()

    private val _operationCompletions = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val operationCompletions: SharedFlow<String> = _operationCompletions.asSharedFlow()

    override fun setProgressState(instanceId: Int, state: ProgressState) {
        _progressMap.update { it + (instanceId to state) }
    }

    override fun removeProgress(instanceId: Int) {
        _progressMap.update { it - instanceId }
    }

    override fun emitError(message: String) {
        _operationErrors.tryEmit(message)
    }

    override fun emitCompletion(message: String) {
        _operationCompletions.tryEmit(message)
    }
}
