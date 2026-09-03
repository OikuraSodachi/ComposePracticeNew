package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FtpUseCase

/** FTP 다운로드 작업을 IoOperation 생명주기로 실행하는 구현체. */
class FtpDownloadOperation(
    private val remotePath: String,
    private val localDestPath: String,
    private val isDirectory: Boolean,
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
        ftpUseCase.download(remotePath, localDestPath, isDirectory) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_DOWNLOAD)
            setProgress(progressState)
            state.progress?.let { myNoti.downloadProgressNoti(it, instanceId) }
        }.collect {}
    }

    override suspend fun onCompletion() {
        myNoti.completedNotification("", completionMessage, ACTION_KEY_DOWNLOAD, instanceId)
        onEmitCompletion(completionMessage)
        onRefresh()
    }

    override suspend fun onError(message: String) {
        myNoti.cancelNotification(instanceId)
        onEmitError(message)
    }
}
