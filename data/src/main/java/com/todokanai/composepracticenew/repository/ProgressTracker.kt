package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks progress state for ongoing file operations (copy, move, delete, zip, unzip). */
@Singleton
class ProgressTracker @Inject constructor() {
    private val _progressState = MutableStateFlow(
        ProgressState(null, totalSize = null, currentSize = null, listSize = null, currentIndex = null)
    )
    val progressState: StateFlow<ProgressState> get() = _progressState

    fun setProgressState(state: ProgressState) {
        _progressState.value = state
    }
}
