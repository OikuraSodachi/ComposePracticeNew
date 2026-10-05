package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.repository.ProgressRepository

/**
 * 작업 완료·실패 시 알림·이벤트 방출·갱신을 일괄 처리하는 콜백.
 * @param notifier 시스템 알림 전송 인터페이스
 * @param progressRepo 완료·오류 이벤트를 방출하는 ProgressRepository
 * @param completionMessage 작업 완료 시 표시할 메시지
 * @param actionKey 완료 알림에 사용할 액션 키. null이면 알림 없음
 * @param onRefresh 작업 완료·실패 후 디렉터리 목록을 갱신하는 콜백
 * @param onErrorOverride 오류 라우팅을 progressRepo 대신 호출부에서 직접 처리할 때 사용. null이면 progressRepo.emitError()로 통일
 */
class OperationCallback(
    private val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    private val completionMessage: String,
    private val actionKey: Int? = null,
    private val onRefresh: suspend () -> Unit,
    private val onErrorOverride: ((String) -> Unit)? = null
) {
    /** 성공 시 완료 알림 전송·이벤트 방출·갱신을 수행한다. */
    suspend fun onSuccess(instanceId: Int) {
        notifier.completedNotification("", completionMessage, actionKey, instanceId)
        progressRepo.emitCompletion(completionMessage)
        onRefresh()
    }

    /** 실패 시 알림 취소·오류 이벤트 방출·갱신을 수행한다. */
    suspend fun onFailure(message: String, instanceId: Int) {
        notifier.cancelNotification(instanceId)
        onErrorOverride?.invoke(message) ?: progressRepo.emitError(message)
        onRefresh()
    }
}
