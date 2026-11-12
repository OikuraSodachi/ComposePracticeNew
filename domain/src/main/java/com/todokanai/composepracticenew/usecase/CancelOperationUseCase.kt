package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.operation.FileOperationOrchestrator

/**
 * 진행 중인 파일 작업을 취소하는 UseCase. 취소한 작업의 진행 알림 제거와 취소 메시지는 FileOperationOrchestrator가 맡는다.
 * @param orchestrator 작업을 실행하고 취소하는 FileOperationOrchestrator
 */
class CancelOperationUseCase(private val orchestrator: FileOperationOrchestrator) {
    /**
     * [instanceId] 작업을 취소한다. 이미 끝난 작업이면 아무것도 하지 않는다.
     * @param instanceId 취소할 작업 인스턴스 ID
     * @return Unit
     */
    fun cancel(instanceId: Int) = orchestrator.cancel(instanceId)
}
