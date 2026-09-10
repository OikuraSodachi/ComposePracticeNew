package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.repository.FileOperationNotifier
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/** 로컬 압축해제 작업을 IoOperation 생명주기로 실행하는 구현체. */
class UnzipOperation(
    private val zipFiles: List<String>,
    private val destPath: String,
    private val unzipHere: Boolean,
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

    override val actionKey = ACTION_KEY_UNZIP

    override suspend fun mainOperation() {
        fileActionUseCase.unzipAction(zipFiles, destPath, unzipHere) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_UNZIP)
            setProgress(progressState)
            state.progress?.let { notifier.unzipProgressNoti(it, instanceId) }
        }.collect {}
    }
}
