package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/** 로컬 압축해제 작업을 IoOperation 생명주기로 실행하는 구현체. */
class UnzipOperation(
    private val zipFiles: List<String>,
    private val destPath: String,
    private val unzipHere: Boolean,
    private val fileActionUseCase: FileActionUseCase,
    private val myNoti: MyNotification,
    private val completionMessage: String,
    private val onRefresh: suspend () -> Unit,
    private val onEmitCompletion: suspend (String) -> Unit,
    private val onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : IoOperation(setProgress, clearProgress) {

    override suspend fun mainOperation() {
        fileActionUseCase.unzipAction(zipFiles, destPath, unzipHere) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_UNZIP)
            setProgress(progressState)
            state.progress?.let { myNoti.unzipProgressNoti(it) }
        }.collect {}
    }

    override suspend fun onCompletion() {
        myNoti.completedNotification("", completionMessage, ACTION_KEY_UNZIP)
        onEmitCompletion(completionMessage)
        onRefresh()
    }

    override suspend fun onError(message: String) {
        onEmitError(message)
        onRefresh()
    }
}
