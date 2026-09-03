package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FtpUseCase

/** FTP 업로드 작업을 IoOperation 생명주기로 실행하는 구현체. */
class FtpUploadOperation(
    private val localPath: String,
    private val remoteDestPath: String,
    private val ftpUseCase: FtpUseCase,
    private val instanceId: Int,
    private val myNoti: MyNotification,
    private val completionMessage: String,
    private val onRefresh: suspend () -> Unit,
    private val onEmitCompletion: suspend (String) -> Unit,
    private val onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : IoOperation(setProgress, clearProgress) {

    override suspend fun mainOperation() {
        ftpUseCase.upload(localPath, remoteDestPath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_UPLOAD)
            setProgress(progressState)
            state.progress?.let { myNoti.uploadProgressNoti(it, instanceId) }
        }.collect {}
    }

    override suspend fun onCompletion() {
        myNoti.completedNotification("", completionMessage, ACTION_KEY_UPLOAD, instanceId)
        onEmitCompletion(completionMessage)
        onRefresh()
    }

    override suspend fun onError(message: String) {
        myNoti.cancelNotification(instanceId)
        onEmitError(message)
    }
}
