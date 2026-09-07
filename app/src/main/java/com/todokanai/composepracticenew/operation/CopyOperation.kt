package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/** 로컬 복사 작업을 IoOperation 생명주기로 실행하는 구현체. */
class CopyOperation(
    private val targetFiles: List<String>,
    private val targetPath: String,
    private val fileActionUseCase: FileActionUseCase,
    myNoti: MyNotification,
    instanceId: Int,
    completionMessage: String,
    onRefresh: suspend () -> Unit,
    onEmitCompletion: suspend (String) -> Unit,
    onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : NotiIoOperation(myNoti, instanceId, completionMessage, onRefresh, onEmitCompletion, onEmitError, setProgress, clearProgress) {

    override val actionKey = ACTION_KEY_COPY

    override suspend fun mainOperation() {
        fileActionUseCase.copyAction(targetFiles, targetPath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_COPY)
            setProgress(progressState)
            state.progress?.let { myNoti.copyProgressNoti(it, instanceId) }
        }.collect {}
    }
}
