package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicInteger

/**
 * 파일 작업의 공통 인프라(인스턴스 ID, 진행 핸들, 콜백, 실행 스코프)를 제공하는 오케스트레이터.
 * @param notifier 파일 작업 시스템 알림을 전송하는 인터페이스
 * @param progressRepo 작업 진행 상태 및 완료·오류 이벤트를 저장·방출하는 ProgressRepository
 * @param appScope 파일 작업 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 */
class FileOperationOrchestrator(
    private val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    private val appScope: CoroutineScope
) {
    private val instanceCounter = AtomicInteger(0)

    /** 새 작업 인스턴스 ID를 할당한 ProgressHandle을 반환한다. */
    fun progressHandle() = ProgressHandle(instanceCounter.incrementAndGet(), progressRepo)

    /**
     * 작업 완료·실패 처리를 담당하는 OperationCallback을 생성한다.
     * @param completionMessage 완료 시 표시할 메시지
     * @param actionKey 완료 알림에 사용할 액션 키. null이면 알림 없음
     * @param onRefresh 완료·실패 후 디렉터리 목록을 갱신하는 콜백
     * @return OperationCallback
     */
    fun callback(
        completionMessage: String,
        actionKey: Int? = null,
        onRefresh: suspend () -> Unit
    ) = OperationCallback(notifier, progressRepo, completionMessage, actionKey, onRefresh)

    /**
     * 파일 작업을 실행하고 진행 상태 보고·알림·완료 처리를 통합한다.
     * 순간 작업(onProgress 미사용)은 progressMap에 등록되지 않아 ForegroundService 보호 대상에서 제외된다.
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param actionKey 작업 종류 식별 키. null이면 진행 알림 없음
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @param block 실제 IO 작업 코드. onProgress 콜백으로 진행 상태를 보고한다
     * @return Unit
     */
    fun launch(
        completionMessage: String,
        actionKey: Int? = null,
        onRefresh: suspend () -> Unit,
        block: suspend (onProgress: (ProgressState) -> Unit) -> Unit
    ) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, actionKey, onRefresh)) {
            block { state ->
                val stateWithKey = state.copy(actionKey = actionKey)
                handle.set(stateWithKey)
                notifier.progressNoti(stateWithKey, handle.instanceId)
            }
        }
    }

    /**
     * 오류 메시지를 progressRepo로 방출한다.
     * @param message 표시할 오류 메시지
     * @return Unit
     */
    fun emitError(message: String) = progressRepo.emitError(message)
}
