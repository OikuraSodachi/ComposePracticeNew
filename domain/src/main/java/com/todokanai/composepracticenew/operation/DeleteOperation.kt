package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/**
 * 로컬 삭제 작업을 IoOperation 생명주기로 실행하는 구현체.
 * @param targetFiles 삭제할 파일 경로 목록
 * @param fileActionUseCase 파일 작업을 실행하는 UseCase
 * @param notifier 파일 작업 알림 전송 인터페이스
 * @param instanceId 작업 인스턴스 식별자
 * @param completionMessage 작업 완료 시 표시할 메시지
 * @param onRefresh 작업 완료 후 파일 목록을 갱신하는 콜백
 * @param onEmitCompletion 완료 메시지를 UI에 전달하는 콜백
 * @param onEmitError 오류 메시지를 UI에 전달하는 콜백
 * @param setProgress 진행 상태를 업데이트하는 콜백
 * @param clearProgress 진행 상태를 초기화하는 콜백
 */
class DeleteOperation(
    private val targetFiles: List<String>,
    private val fileActionUseCase: FileActionUseCase,
    notifier: FileOperationNotifier,
    instanceId: Int,
    completionMessage: String,
    onRefresh: suspend () -> Unit,
    onEmitCompletion: suspend (String) -> Unit,
    onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : NotiIoOperation(notifier, instanceId, completionMessage, onRefresh, onEmitCompletion, onEmitError, setProgress, clearProgress) {

    override val actionKey = ACTION_KEY_DELETE

    override suspend fun mainOperation() {
        targetFiles.forEach { path ->
            fileActionUseCase.deleteFile(path) { state ->
                progressState = state.copy(actionKey = ACTION_KEY_DELETE)
                setProgress(progressState)
                val idx = state.currentIndex ?: return@deleteFile
                val total = state.listSize ?: return@deleteFile
                notifier.deleteProgressNoti(idx, total, instanceId)
            }.collect {}
        }
    }
}
