package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase

/** 로컬 이동 작업을 IoOperation 생명주기로 실행하는 구현체. */
class MoveOperation(
    private val targetFiles: List<String>,
    private val targetPath: String,
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
        fileActionUseCase.moveFile(targetFiles, targetPath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_MOVE)
            progressUseCase.setProgressState(instanceId, progressState)
            state.progress?.let { myNoti.moveProgressNoti(it) }
        }.collect {}
    }

    override suspend fun onCompletion() {
        myNoti.completedNotification("", completionMessage, ACTION_KEY_MOVE)
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
