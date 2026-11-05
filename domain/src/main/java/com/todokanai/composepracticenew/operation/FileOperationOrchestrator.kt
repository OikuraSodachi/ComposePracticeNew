package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 실패 메시지가 없을 때 표시할 메시지. */
private const val UNKNOWN_FAILURE_MESSAGE = "알 수 없는 오류"

/**
 * 파일 작업의 공통 인프라(인스턴스 ID, 진행 핸들, 콜백, 실행 스코프)를 제공하는 오케스트레이터.
 * 작업 인스턴스 ID는 이 클래스만 발급한다. 진행 핸들·완료 콜백·실행 엔진을 이 클래스 안에 private으로 두어,
 * 작업을 띄우는 길은 [launch] 하나뿐이고 발급하지 않은 ID로 작업을 띄울 수 없다(같은 모듈 안에서도).
 * @param notifier 파일 작업 시스템 알림을 전송하는 인터페이스
 * @param progressRepo 작업 진행 상태를 저장하는 ProgressRepository
 * @param notices 작업의 완료·실패 메시지를 화면에 알리도록 맡기는 OperationNoticeReporter
 * @param protection 진행 표시가 있는 작업이 시작 전에 받는 프로세스 보호
 * @param appScope 파일 작업 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 */
class FileOperationOrchestrator(
    private val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    private val notices: OperationNoticeReporter,
    private val protection: OperationProtection,
    private val appScope: CoroutineScope
) {
    /** ID 발급과 진행 상태 등록을 한 단위로 묶는 잠금. 동시에 시작한 작업들이 progressMap에 ID 순서대로 나타나게 한다. */
    private val startLock = Any()

    /** 마지막으로 발급한 작업 인스턴스 ID. [startLock] 안에서만 읽고 쓴다. */
    private var lastInstanceId = 0

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
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param operationType 작업 종류. null이면 진행 알림 없음
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @param block 실제 IO 작업 코드. onProgress 콜백으로 진행 상태를 보고하고, 실패는 결과로 돌려준다
     * @return Unit
     */
    fun launch(
        completionMessage: String,
        operationType: FileOperationType? = null,
        onRefresh: suspend () -> Unit,
        block: suspend (onProgress: (ProgressState) -> Unit) -> IoResult<Unit>
    ) {
        val handle = startHandle(operationType)
        val callback = OperationCallback(completionMessage, onRefresh)
        launchOperation(handle, callback) {
            if (operationType != null) protection.acquire().valueOr { return@launchOperation it }
            block { state ->
                val stateWithType = state.copy(operationType = operationType)
                handle.set(stateWithType)
                notifier.progressNoti(stateWithType, handle.instanceId)
            }
        }
    }

    /**
     * 파일 작업을 [appScope]에서 실행하며 완료·실패·진행 상태를 통합 관리하는 실행 엔진.
     * 작업의 실패는 결과([IoResult.Failure])로 받는다. 예외는 구현 오류이지만, 작업 하나 때문에 앱 수명 스코프가 무너지지 않도록
     * 이곳에서만 실패로 바꿔 그 작업만 끝낸다.
     * @param progressHandle 작업 인스턴스의 진행 상태 핸들
     * @param callback 완료·실패 시 알림·이벤트·갱신을 처리하는 콜백
     * @param work 실제 IO 작업 코드
     */
    private fun launchOperation(
        progressHandle: ProgressHandle,
        callback: OperationCallback,
        work: suspend () -> IoResult<Unit>
    ) {
        appScope.launch {
            try {
                when (val result = work()) {
                    is IoResult.Success -> callback.onSuccess(progressHandle.instanceId)
                    is IoResult.Failure -> callback.onFailure(result.message ?: UNKNOWN_FAILURE_MESSAGE, progressHandle.instanceId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                callback.onFailure(e.message ?: UNKNOWN_FAILURE_MESSAGE, progressHandle.instanceId)
            } finally {
                progressHandle.clear()
            }
        }
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
        /** [state]를 이 인스턴스의 진행 상태로 업데이트한다. */
        fun set(state: ProgressState) = progressRepo.setProgressState(instanceId, state)

        /** 이 인스턴스의 진행 상태를 제거한다. */
        fun clear() = progressRepo.removeProgress(instanceId)
    }

    /**
     * 작업 완료·실패 시 알림·메시지 보관·갱신을 일괄 처리하는 콜백.
     * 순간 작업(진행 알림 없음)도 완료하면 완료 알림을 띄운다.
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료·실패 후 디렉터리 목록을 갱신하는 콜백
     */
    private inner class OperationCallback(
        private val completionMessage: String,
        private val onRefresh: suspend () -> Unit
    ) {
        /** 성공 시 완료 알림 전송·완료 메시지 보관·갱신을 수행한다. */
        suspend fun onSuccess(instanceId: Int) {
            notifier.completedNotification(completionMessage, instanceId)
            notices.report(OperationNoticeKind.COMPLETION, completionMessage)
            onRefresh()
        }

        /** 실패 시 알림 취소·실패 메시지 보관·갱신을 수행한다. 진행 알림은 지우므로, 실패는 보관한 메시지로만 알린다. */
        suspend fun onFailure(message: String, instanceId: Int) {
            notifier.cancelNotification(instanceId)
            notices.report(OperationNoticeKind.FAILURE, message)
            onRefresh()
        }
    }
}
