package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FtpUseCase

/** FTP 다운로드 작업을 IoOperation 생명주기로 실행하는 구현체. */
class FtpDownloadOperation(
    private val remotePath: String,
    private val localDestPath: String,
    private val isDirectory: Boolean,
    private val ftpUseCase: FtpUseCase,
    myNoti: MyNotification,
    instanceId: Int,
    completionMessage: String,
    onRefresh: suspend () -> Unit,
    onEmitCompletion: suspend (String) -> Unit,
    onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : NotiIoOperation(myNoti, instanceId, completionMessage, onRefresh, onEmitCompletion, onEmitError, setProgress, clearProgress) {

    override val actionKey = ACTION_KEY_DOWNLOAD

    override suspend fun mainOperation() {
        ftpUseCase.download(remotePath, localDestPath, isDirectory) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_DOWNLOAD)
            setProgress(progressState)
            state.progress?.let { myNoti.downloadProgressNoti(it, instanceId) }
        }.collect {}
    }
}
