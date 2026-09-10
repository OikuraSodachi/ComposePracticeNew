package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase

/** 로컬 삭제 작업을 IoOperation 생명주기로 실행하는 구현체. */
class DeleteOperation(
    private val targetFiles: List<String>,
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

    override val actionKey = ACTION_KEY_DELETE

    override suspend fun mainOperation() {
        targetFiles.forEach { path ->
            fileActionUseCase.deleteFile(path) { state ->
                progressState = state.copy(actionKey = ACTION_KEY_DELETE)
                setProgress(progressState)
                val idx = state.currentIndex ?: return@deleteFile
                val total = state.listSize ?: return@deleteFile
                myNoti.deleteProgressNoti(idx, total, instanceId)
            }.collect {}
        }
    }
}
