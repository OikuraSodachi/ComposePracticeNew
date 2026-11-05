package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.repository.ProgressRepository

/**
 * 작업 완료·실패 시 알림·이벤트 방출·갱신을 일괄 처리하는 콜백. [FileOperationOrchestrator]만 만든다.
 * 순간 작업(진행 알림 없음)도 완료하면 완료 알림을 띄운다.
 * @param notifier 시스템 알림 전송 인터페이스
 * @param progressRepo 완료·오류 이벤트를 방출하는 ProgressRepository
 * @param completionMessage 작업 완료 시 표시할 메시지
 * @param onRefresh 작업 완료·실패 후 디렉터리 목록을 갱신하는 콜백
 */
internal class OperationCallback(
    private val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    private val completionMessage: String,
    private val onRefresh: suspend () -> Unit
) {
    /** 성공 시 완료 알림 전송·이벤트 방출·갱신을 수행한다. */
    suspend fun onSuccess(instanceId: Int) {
        notifier.completedNotification(completionMessage, instanceId)
        progressRepo.emitCompletion(completionMessage)
        onRefresh()
    }

    /** 실패 시 알림 취소·오류 이벤트 방출·갱신을 수행한다. */
    suspend fun onFailure(message: String, instanceId: Int) {
        notifier.cancelNotification(instanceId)
        progressRepo.emitError(message)
        onRefresh()
    }
}
