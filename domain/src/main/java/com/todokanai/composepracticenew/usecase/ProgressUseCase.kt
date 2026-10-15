package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * 진행 중인 파일 작업의 progress 상태를 읽고 업데이트하며, 작업 실패 이벤트를 중계하는 UseCase.
 * 알림 클릭으로 진행률 다이얼로그를 재표시하는 트리거 상태도 관리한다.
 * @param repo 진행 상태 및 이벤트를 저장·방출하는 ProgressRepository 구현체
 */
class ProgressUseCase(private val repo: ProgressRepository) {
    private val instanceCounter = AtomicInteger(0)

    private val _showProgressDialogForKey = MutableStateFlow<Int?>(null)
    /** 알림 클릭으로 로컬 작업 진행률 다이얼로그를 재표시해야 하는 actionKey. null이면 신호 없음. */
    val showProgressDialogForKey: StateFlow<Int?> = _showProgressDialogForKey.asStateFlow()

    private val _showRemoteProgressDialogForKey = MutableStateFlow<Int?>(null)
    /** 알림 클릭으로 원격 전송 진행률 다이얼로그를 재표시해야 하는 actionKey. null이면 신호 없음. */
    val showRemoteProgressDialogForKey: StateFlow<Int?> = _showRemoteProgressDialogForKey.asStateFlow()

    /**
     * 알림 클릭 시 진행률 다이얼로그를 재표시하도록 요청한다.
     * @param actionKey 재표시할 작업 식별 키 — 원격 작업이면 원격 다이얼로그, 아니면 로컬 다이얼로그 트리거
     * @return Unit
     */
    fun requestShowDialog(actionKey: Int) {
        if (OperationConstants.isRemoteOperation(actionKey)) {
            _showRemoteProgressDialogForKey.value = actionKey
        } else {
            _showProgressDialogForKey.value = actionKey
        }
    }

    /**
     * 로컬 작업 진행률 다이얼로그가 표시됐음을 알려 트리거를 초기화한다.
     * @return Unit
     */
    fun onProgressDialogShown() { _showProgressDialogForKey.value = null }

    /**
     * 원격 전송 진행률 다이얼로그가 표시됐음을 알려 트리거를 초기화한다.
     * @return Unit
     */
    fun onRemoteProgressDialogShown() { _showRemoteProgressDialogForKey.value = null }

    /** 현재 진행 중인 모든 파일 작업의 진행 상태를 인스턴스 id 기준으로 저장하는 StateFlow. */
    val progressMap: StateFlow<Map<Int, ProgressState>> = repo.progressMap
    /** 파일 작업 실패 시 발생하는 오류 메시지 이벤트. */
    val operationErrors: SharedFlow<String> = repo.operationErrors
    /** 파일 작업 완료 시 발생하는 완료 메시지 이벤트. */
    val operationCompletions: SharedFlow<String> = repo.operationCompletions

    /**
     * 작업 인스턴스마다 전역적으로 고유한 ID를 발급한다.
     * @return 새로 발급된 고유 인스턴스 id (1씩 증가)
     */
    fun nextInstanceId(): Int = instanceCounter.incrementAndGet()
    /**
     * 특정 작업 인스턴스의 진행 상태를 갱신한다.
     * @param instanceId 갱신할 작업 인스턴스의 고유 id
     * @param state 새로 설정할 ProgressState
     * @return Unit
     */
    fun setProgressState(instanceId: Int, state: ProgressState) = repo.setProgressState(instanceId, state)

    /**
     * 완료된 작업 인스턴스의 진행 상태를 제거한다.
     * @param instanceId 제거할 작업 인스턴스의 고유 id
     * @return Unit
     */
    fun removeProgress(instanceId: Int) = repo.removeProgress(instanceId)

    /**
     * 파일 작업 오류 이벤트를 방출한다.
     * @param message 오류 내용을 설명하는 메시지
     * @return Unit
     */
    fun emitError(message: String) = repo.emitError(message)

    /**
     * 파일 작업 완료 이벤트를 방출한다.
     * @param message 완료 내용을 설명하는 메시지
     * @return Unit
     */
    fun emitCompletion(message: String) = repo.emitCompletion(message)
}
