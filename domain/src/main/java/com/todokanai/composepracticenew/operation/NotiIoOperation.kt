package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.FileOperationNotifier

/**
 * 알림을 사용하는 파일 작업 공통 생명주기(완료 알림·오류 처리·갱신)를 템플릿으로 제공하는 추상 클래스.
 */
abstract class NotiIoOperation(
    protected val notifier: FileOperationNotifier,
    protected val instanceId: Int,
    private val completionMessage: String,
    private val onRefresh: suspend () -> Unit,
    private val onEmitCompletion: suspend (String) -> Unit,
    private val onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : IoOperation(setProgress, clearProgress) {

    protected abstract val actionKey: Int

    override suspend fun onCompletion() {
        notifier.completedNotification("", completionMessage, actionKey, instanceId)
        onEmitCompletion(completionMessage)
        onRefresh()
    }

    override suspend fun onError(message: String) {
        notifier.cancelNotification(instanceId)
        onEmitError(message)
        onRefresh()
    }
}
