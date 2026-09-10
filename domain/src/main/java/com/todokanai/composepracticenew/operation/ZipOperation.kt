package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.repository.FileOperationNotifier
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/** 로컬 압축 작업을 IoOperation 생명주기로 실행하는 구현체. */
class ZipOperation(
    private val sourceFiles: List<String>,
    private val zipFilePath: String,
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

    override val actionKey = ACTION_KEY_ZIP

    override suspend fun mainOperation() {
        fileActionUseCase.zipAction(sourceFiles, zipFilePath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_ZIP)
            setProgress(progressState)
            state.progress?.let { notifier.zipProgressNoti(it, instanceId) }
        }.collect {}
    }
}
