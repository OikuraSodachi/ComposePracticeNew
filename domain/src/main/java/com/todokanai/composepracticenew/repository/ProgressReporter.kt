package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState

/**
 * 진행 중인 파일 작업의 progress 상태를 instanceId별로 쓰는 쪽의 계약. 읽는 쪽 계약은 [ProgressRepository]다.
 * 인스턴스 ID를 발급하는 FileOperationOrchestrator만 주입받는다 — 읽는 쪽이 발급하지 않은 ID로 진행 상태를 쓰지 못하도록(#444).
 */
interface ProgressReporter {
    /**
     * [instanceId] 작업의 진행 상태를 [state]로 바꾼다.
     * @param instanceId 작업 인스턴스 식별자
     * @param state 새 진행 상태
     */
    fun setProgressState(instanceId: Int, state: ProgressState)

    /**
     * [instanceId] 작업의 진행 상태를 지운다.
     * @param instanceId 작업 인스턴스 식별자
     */
    fun removeProgress(instanceId: Int)
}
