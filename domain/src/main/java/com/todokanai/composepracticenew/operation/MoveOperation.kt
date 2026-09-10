package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.repository.FileOperationNotifier
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/** 로컬 이동 작업을 IoOperation 생명주기로 실행하는 구현체. */
class MoveOperation(
    private val targetFiles: List<String>,
    private val targetPath: String,
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

    override val actionKey = ACTION_KEY_MOVE

    override suspend fun mainOperation() {
        fileActionUseCase.moveFile(targetFiles, targetPath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_MOVE)
            setProgress(progressState)
            state.progress?.let { notifier.moveProgressNoti(it, instanceId) }
        }.collect {}
    }
}
