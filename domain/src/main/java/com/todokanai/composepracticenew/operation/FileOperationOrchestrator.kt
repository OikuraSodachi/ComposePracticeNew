package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.repository.ConflictQuestionAsker
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 파일 작업의 공통 인프라(인스턴스 ID, 진행 핸들, 콜백, 실행 스코프)를 제공하는 오케스트레이터.
 * 작업 인스턴스 ID는 이 클래스만 발급한다. 진행 핸들·완료 콜백·실행 엔진을 이 클래스 안에 private으로 두어,
 * 작업을 띄우는 길은 [launch] 하나뿐이고 발급하지 않은 ID로 작업을 띄울 수 없다(같은 모듈 안에서도).
 * @param notifier 파일 작업 시스템 알림을 전송하는 인터페이스
 * @param progressRepo 작업 진행 상태를 저장하는 ProgressRepository
 * @param notices 작업의 완료·실패 메시지를 화면에 알리도록 맡기는 OperationNoticeReporter
 * @param protection 진행 표시가 있는 작업이 시작 전에 받는 프로세스 보호
 * @param questions 쓰기 충돌을 사용자에게 묻고 답을 기다리는 통로
 * @param appScope 파일 작업 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 */
class FileOperationOrchestrator(
    private val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    private val notices: OperationNoticeReporter,
    private val protection: OperationProtection,
    private val questions: ConflictQuestionAsker,
    private val appScope: CoroutineScope
) {
    /** ID 발급과 진행 상태 등록을 한 단위로 묶는 잠금. 동시에 시작한 작업들이 progressMap에 ID 순서대로 나타나게 한다. */
    private val startLock = Any()

    /** 마지막으로 발급한 작업 인스턴스 ID. [startLock] 안에서만 읽고 쓴다. */
    private var lastInstanceId = 0

    /** 실행 중인 작업의 Job을 작업 인스턴스 ID로 찾도록 둔 표. 작업이 끝나면 빠진다. */
    private val runningJobs = ConcurrentHashMap<Int, Job>()

    /**
     * [instanceId] 작업을 취소한다. 이미 끝났거나 없는 작업이면 아무것도 하지 않는다.
     * 작업은 다음 진행 보고나 일시 중단 지점에서 멈추고, 취소를 알린다([OperationCallback.onCancelled]).
     * @param instanceId 취소할 작업 인스턴스 ID
     * @return Unit
     */
    fun cancel(instanceId: Int) {
        runningJobs[instanceId]?.cancel()
    }

    /**
     * 새 작업 인스턴스 ID를 할당한 ProgressHandle을 반환한다. ID는 발급 순서대로 커지므로, 더 큰 ID가 더 나중에 시작한 작업이다.
     * 진행 표시가 있는 작업은 발급과 동시에 진행 상태(진행률 보고 전)를 등록한다. 그래서 progressMap에 나타나는 시점이 곧 작업 시작이다.
     * @param operationType 작업 종류. null이면 순간 작업이라 등록하지 않는다
     * @return 발급한 ID의 ProgressHandle
     */
    private fun startHandle(operationType: FileOperationType?): ProgressHandle = synchronized(startLock) {
        val handle = ProgressHandle(++lastInstanceId, progressRepo)
        if (operationType != null) handle.set(ProgressState(operationType = operationType))
        handle
    }

    /**
     * 파일 작업을 실행하고 진행 상태 보고·알림·완료 처리를 통합한다.
     * 진행 표시가 있는 작업은 시작하자마자 progressMap에 등록되고, [block]을 실행하기 전에 프로세스 보호를 받는다([OperationProtection.acquire]).
     * 보호가 거부되면 [block]을 실행하지 않고(파일을 건드리지 않고) 실패로 끝낸다.
     * 보호는 시스템의 실행 시간 한도까지이며, 한도 이후의 보호는 지원 범위 밖이다.
     * 순간 작업(operationType null)은 보호를 요청하지 않는다.
     * 취소([cancel])는 onProgress 안에서 확인한다 — 취소된 뒤의 진행 보고는 CancellationException을 던지므로, 진행을 보고하는 작업은 따로 확인하지 않아도 다음 보고에서 멈춘다.
     * 완료·취소 메시지는 [operationType]으로 정한다.
     * @param operationType 작업 종류. null이면 진행 알림 없음
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @param block 실제 IO 작업 코드. onProgress 콜백으로 진행 상태를 보고하고, 실패는 결과로 돌려준다. onProgress가 던진 취소는 잡지 않고 전파한다
     * @return Unit
     */
    fun launch(
        operationType: FileOperationType? = null,
        onRefresh: suspend () -> Unit,
        block: suspend (onProgress: (ProgressState) -> Unit) -> IoResult<Unit>
    ) {
        val handle = startHandle(operationType)
        val callback = OperationCallback(operationType, onRefresh)
        launchOperation(handle, callback) {
            if (operationType != null) protection.acquire().valueOr { return@launchOperation OperationOutcome.Finished(it) }
            OperationOutcome.Finished(block(progressReporterOf(handle, operationType)))
        }
    }

    /**
     * [launch]와 같이 파일 작업을 실행하되, [block]이 쓰기 중에 만난 같은 이름의 항목을 사용자에게 묻는 통로를 함께 넘긴다.
     * 묻는 동안 작업은 멈추고 진행 알림은 답을 기다리는 상태가 된다. 답을 받으면 마지막 진행 상태로 알림을 되돌린다.
     * 사용자가 질문에서 취소하면 취소로, 질문 하나에 [MAX_CONFLICT_ANSWER_WAIT] 동안 답이 없으면 실패로 끝낸다. 어느 쪽이든 이미 처리한 항목은 그대로 둔다.
     * "나머지에도 적용"한 답은 이 작업 안에서만 쓴다.
     * @param operationType 작업 종류. 묻는 작업은 진행 표시가 있는 작업이다
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @param block 실제 IO 작업 코드. 진행 보고 콜백과 충돌을 묻는 통로를 받고, 실패는 결과로 돌려준다
     * @return Unit
     */
    fun launchResolvingConflicts(
        operationType: FileOperationType,
        onRefresh: suspend () -> Unit,
        block: suspend (onProgress: (ProgressState) -> Unit, resolveConflict: WriteConflictResolver) -> IoResult<Unit>
    ) {
        val handle = startHandle(operationType)
        val callback = OperationCallback(operationType, onRefresh)
        launchOperation(handle, callback) {
            protection.acquire().valueOr { return@launchOperation OperationOutcome.Finished(it) }
            val resolver = OperationConflictResolver(
                operationType = operationType,
                questions = questions,
                onAwaiting = { notifier.awaitingAnswerNoti(operationType, handle.instanceId) },
                onAnswered = { handle.latest?.let { notifier.progressNoti(it, handle.instanceId) } },
            )
            try {
                OperationOutcome.Finished(block(progressReporterOf(handle, operationType), resolver))
            } catch (e: UnansweredConflict) {
                OperationOutcome.Unanswered(operationType, e.conflict.name)
            }
        }
    }

    /**
     * [handle] 작업의 진행 보고 콜백을 만든다. 보고마다 취소를 확인하고, 진행 상태와 진행 알림을 갱신한다.
     * 진행 보고는 작업이 고른 스레드에서 일시 중단 없이 부르므로, 이 작업의 Job을 잡아 두고 그것으로 취소를 확인한다.
     * @param handle 작업 인스턴스의 진행 상태 핸들
     * @param operationType 작업 종류. 보고한 상태에 붙인다
     * @return 진행 보고 콜백. 취소된 뒤에 부르면 CancellationException을 던진다
     */
    private suspend fun progressReporterOf(handle: ProgressHandle, operationType: FileOperationType?): (ProgressState) -> Unit {
        val job = currentCoroutineContext().job
        return { state ->
            job.ensureActive()
            val stateWithType = state.copy(operationType = operationType)
            handle.set(stateWithType)
            notifier.progressNoti(stateWithType, handle.instanceId)
        }
    }

    /**
     * 파일 작업을 [appScope]에서 실행하며 완료·실패·진행 상태를 통합 관리하는 실행 엔진.
     * 작업의 실패는 결과([IoResult.Failure])로 받는다. 작업이 던진 예외는 구현 오류이지만, 작업 하나 때문에 앱 수명 스코프가 무너지지 않도록
     * 이곳에서만 실패로 바꿔 그 작업만 끝낸다. 취소는 결과로 바꾸지 않고 알린 뒤 전파한다.
     * 결과 보고([callback])는 그 경계 밖에서 한 번만 한다 — 보고 중 예외를 작업 실패로 바꾸면 이미 완료를 알린 작업을 실패로 또 알리게 되므로(#365).
     * 보고 중 예외는 구현 오류로 [appScope]에 전파한다.
     * Job은 [runningJobs]에 넣은 뒤에 시작한다 — 시작하자마자 취소를 요청해도 찾을 수 있도록.
     * @param progressHandle 작업 인스턴스의 진행 상태 핸들
     * @param callback 완료·실패·취소 시 알림·이벤트·갱신을 처리하는 콜백
     * @param work 실제 IO 작업 코드
     */
    private fun launchOperation(
        progressHandle: ProgressHandle,
        callback: OperationCallback,
        work: suspend () -> OperationOutcome
    ) {
        val instanceId = progressHandle.instanceId
        val job = appScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = runWork(instanceId, callback, work)
                // 작업이 끝난 뒤에 온 취소 요청은 결과를 바꾸지 않는다 — 결과 알림·목록 갱신을 끝까지 한다
                withContext(NonCancellable) {
                    when (outcome) {
                        is OperationOutcome.Finished -> when (val result = outcome.result) {
                            is IoResult.Success -> callback.onSuccess(instanceId)
                            is IoResult.Failure -> callback.onOperationFailed(result, instanceId)
                        }
                        is OperationOutcome.Unanswered -> callback.onFailure(
                            NoticeMessage.UnansweredConflict(outcome.operationType, outcome.name, MAX_CONFLICT_ANSWER_WAIT.inWholeHours),
                            instanceId
                        )
                    }
                }
            } finally {
                progressHandle.clear()
            }
        }
        runningJobs[instanceId] = job
        job.invokeOnCompletion { runningJobs.remove(instanceId) }
        job.start()
    }

    /**
     * [work]를 실행해 결과를 돌려준다. 작업이 던진 예외(구현 오류)는 그 메시지의 실패 결과로 바꾼다.
     * 취소되면 취소를 알린 뒤 전파한다.
     * @param instanceId 작업 인스턴스 ID
     * @param callback 취소를 알릴 콜백
     * @param work 실제 IO 작업 코드
     * @return 작업 결과
     */
    private suspend fun runWork(instanceId: Int, callback: OperationCallback, work: suspend () -> OperationOutcome): OperationOutcome =
        try {
            work()
        } catch (e: CancellationException) {
            // 취소된 코루틴에서도 알림 제거·메시지·목록 갱신을 끝까지 한다
            withContext(NonCancellable) { callback.onCancelled(instanceId) }
            throw e
        } catch (e: Exception) {
            OperationOutcome.Finished(IoResult.ItemFailed(e.message?.let(FailureReason::Raw)))
        }

    /** 작업 실행의 결과. 작업이 돌려준 결과와, 충돌 질문에 답이 없어 멈춘 경우를 나눈다. */
    private sealed interface OperationOutcome {
        /**
         * 작업이 끝까지 실행돼 결과를 돌려줬다(보호를 받지 못한 실패 포함).
         * @param result 작업 결과
         */
        data class Finished(val result: IoResult<Unit>) : OperationOutcome

        /**
         * 충돌 질문 하나에 [MAX_CONFLICT_ANSWER_WAIT] 동안 답이 없어 멈췄다.
         * @param operationType 멈춘 작업의 종류
         * @param name 답을 받지 못한 항목의 이름
         */
        data class Unanswered(val operationType: FileOperationType, val name: String) : OperationOutcome
    }

    /**
     * 단일 작업 인스턴스의 진행 상태를 관리하는 핸들. [startHandle]만 만든다.
     * @param instanceId 작업 인스턴스 식별자
     * @param progressRepo 진행 상태를 저장·제거하는 ProgressRepository
     */
    private class ProgressHandle(
        val instanceId: Int,
        private val progressRepo: ProgressRepository
    ) {
        /** 마지막으로 [set]한 진행 상태. 충돌 질문에 답한 뒤 진행 알림을 되돌릴 때 쓴다. 작업 스레드에서 쓰고 다른 스레드에서도 읽으므로 @Volatile이다. */
        @Volatile
        var latest: ProgressState? = null
            private set

        /** [state]를 이 인스턴스의 진행 상태로 업데이트한다. */
        fun set(state: ProgressState) {
            latest = state
            progressRepo.setProgressState(instanceId, state)
        }

        /** 이 인스턴스의 진행 상태를 제거한다. */
        fun clear() = progressRepo.removeProgress(instanceId)
    }

    /**
     * 작업 완료·실패·취소 시 알림·메시지 보관·갱신을 일괄 처리하는 콜백.
     * 순간 작업(진행 알림 없음)도 완료하면 완료 알림을 띄운다. 완료·취소 메시지는 작업 종류로 정한다.
     * @param operationType 작업 종류. null이면 순간 작업
     * @param onRefresh 작업 완료·실패·취소 후 디렉터리 목록을 갱신하는 콜백
     */
    private inner class OperationCallback(
        private val operationType: FileOperationType?,
        private val onRefresh: suspend () -> Unit
    ) {
        /** 성공 시 완료 알림 전송·완료 메시지 보관·갱신을 수행한다. */
        suspend fun onSuccess(instanceId: Int) {
            notifier.completedNotification(operationType, instanceId)
            notices.report(OperationNoticeKind.COMPLETION, NoticeMessage.Completed(operationType))
            onRefresh()
        }

        /**
         * 작업이 실패 결과를 돌려줬을 때 그 실패를 작업 종류와 함께 알린다. 부분 실패의 머리말은 작업 종류로 정해지므로 함께 담는다.
         * @param failure 작업이 돌려준 실패
         * @param instanceId 작업 인스턴스 ID
         */
        suspend fun onOperationFailed(failure: IoResult.Failure, instanceId: Int) =
            onFailure(NoticeMessage.OperationFailed(operationType, failure), instanceId)

        /** 실패 시 알림 취소·실패 메시지 보관·갱신을 수행한다. 진행 알림은 지우므로, 실패는 보관한 메시지로만 알린다. */
        suspend fun onFailure(message: NoticeMessage, instanceId: Int) {
            notifier.cancelNotification(instanceId)
            notices.report(OperationNoticeKind.FAILURE, message)
            onRefresh()
        }

        /** 취소 시 진행 알림 제거·취소 메시지 보관·갱신을 수행한다. 사용자가 요청한 결과이므로 완료와 같은 종류로 알린다. */
        suspend fun onCancelled(instanceId: Int) {
            notifier.cancelNotification(instanceId)
            notices.report(OperationNoticeKind.COMPLETION, NoticeMessage.Cancelled(operationType))
            onRefresh()
        }
    }
}
