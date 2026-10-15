package com.todokanai.composepracticenew.operation

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
    // TODO: notifier·appScope를 public val로 노출하면 UseCase가 내부 인프라에 직접 접근하게 된다.
    //  private으로 내리고 launch(completionMessage, actionKey, onRefresh, block) 메서드를 추가해
    //  UseCase가 스코프·알림 구현에 의존하지 않도록 개선 필요.
    val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    val appScope: CoroutineScope
) {
    private val instanceCounter = AtomicInteger(0)

    /** 새 작업 인스턴스 ID를 할당한 ProgressHandle을 반환한다. */
    fun progressHandle() = ProgressHandle(instanceCounter.incrementAndGet(), progressRepo)

    /**
     * 작업 완료·실패 처리를 담당하는 OperationCallback을 생성한다.
     * @param completionMessage 완료 시 표시할 메시지
     * @param actionKey 완료 알림에 사용할 액션 키. null이면 알림 없음
     * @param onRefresh 완료·실패 후 디렉터리 목록을 갱신하는 콜백
     * @param onErrorOverride 오류를 progressRepo 대신 직접 처리할 때 사용. null이면 progressRepo.emitError()로 통일
     * @return OperationCallback
     */
    fun callback(
        completionMessage: String,
        actionKey: Int? = null,
        onRefresh: suspend () -> Unit,
        onErrorOverride: ((String) -> Unit)? = null
    ) = OperationCallback(notifier, progressRepo, completionMessage, actionKey, onRefresh, onErrorOverride)

    /**
     * 오류 메시지를 progressRepo로 방출한다.
     * @param message 표시할 오류 메시지
     * @return Unit
     */
    fun emitError(message: String) = progressRepo.emitError(message)
}
