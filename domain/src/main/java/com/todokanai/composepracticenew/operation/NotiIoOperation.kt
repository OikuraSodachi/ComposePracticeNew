package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState

/**
 * 알림을 사용하는 파일 작업 공통 생명주기(완료 알림·오류 처리·갱신)를 템플릿으로 제공하는 추상 클래스.
 * @param notifier 파일 작업 알림 전송 인터페이스
 * @param instanceId 작업 인스턴스 식별자
 * @param completionMessage 작업 완료 시 표시할 메시지
 * @param onRefresh 작업 완료 후 파일 목록을 갱신하는 콜백
 * @param onEmitCompletion 완료 메시지를 UI에 전달하는 콜백
 * @param onEmitError 오류 메시지를 UI에 전달하는 콜백
 * @param setProgress 진행 상태를 업데이트하는 콜백
 * @param clearProgress 진행 상태를 초기화하는 콜백
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
