package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.ProgressRepository

/**
 * 단일 작업 인스턴스의 진행 상태를 관리하는 핸들.
 * @param instanceId 작업 인스턴스 식별자
 * @param progressRepo 진행 상태를 저장·제거하는 ProgressRepository
 */
class ProgressHandle(
    val instanceId: Int,
    private val progressRepo: ProgressRepository
) {
    /** [state]를 이 인스턴스의 진행 상태로 업데이트한다. */
    fun set(state: ProgressState) = progressRepo.setProgressState(instanceId, state)

    /** 이 인스턴스의 진행 상태를 제거한다. */
    fun clear() = progressRepo.removeProgress(instanceId)
}
