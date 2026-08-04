package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.StateFlow

/** 진행 중인 파일 작업의 progress 상태를 actionKey별로 관리하는 Repository 인터페이스. */
interface ProgressRepository {
    val progressMap: StateFlow<Map<Int, ProgressState>>
    fun setProgressState(actionKey: Int, state: ProgressState)
    fun removeProgress(actionKey: Int)
}
