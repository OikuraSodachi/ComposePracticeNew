package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase

/** 로컬 삭제 작업을 IoOperation 생명주기로 실행하는 구현체. */
class DeleteOperation(
    private val targetFiles: List<String>,
    private val fileActionUseCase: FileActionUseCase,
    private val progressUseCase: ProgressUseCase,
    private val instanceId: Int,
    private val myNoti: MyNotification,
    private val completionMessage: String,
    private val onRefresh: suspend () -> Unit,
    private val onEmitCompletion: suspend (String) -> Unit,
    private val onEmitError: suspend (String) -> Unit
) : IoOperation() {

    override suspend fun mainOperation() {
        targetFiles.forEach { path ->
            fileActionUseCase.deleteFile(path) { state ->
                progressState = state.copy(actionKey = ACTION_KEY_DELETE)
                progressUseCase.setProgressState(instanceId, progressState)
                val idx = state.currentIndex ?: return@deleteFile
                val total = state.listSize ?: return@deleteFile
                myNoti.deleteProgressNoti(idx, total)
            }.collect {}
        }
    }

    override suspend fun onCompletion() {
        myNoti.completedNotification("", completionMessage, ACTION_KEY_DELETE)
        onEmitCompletion(completionMessage)
        onRefresh()
    }

    override suspend fun progressTracker() {
        progressUseCase.setProgressState(instanceId, progressState)
    }

    override suspend fun removeProgress() {
        progressUseCase.removeProgress(instanceId)
    }

    override suspend fun onError(message: String) {
        onEmitError(message)
        onRefresh()
    }
}
