package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.repository.FileOperationNotifier
import com.todokanai.composepracticenew.usecase.FtpUseCase

/** FTP 업로드 작업을 IoOperation 생명주기로 실행하는 구현체. */
class FtpUploadOperation(
    private val localPath: String,
    private val remoteDestPath: String,
    private val ftpUseCase: FtpUseCase,
    notifier: FileOperationNotifier,
    instanceId: Int,
    completionMessage: String,
    onRefresh: suspend () -> Unit,
    onEmitCompletion: suspend (String) -> Unit,
    onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : NotiIoOperation(notifier, instanceId, completionMessage, onRefresh, onEmitCompletion, onEmitError, setProgress, clearProgress) {

    override val actionKey = ACTION_KEY_UPLOAD

    override suspend fun mainOperation() {
        ftpUseCase.upload(localPath, remoteDestPath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_UPLOAD)
            setProgress(progressState)
            state.progress?.let { notifier.uploadProgressNoti(it, instanceId) }
        }.collect {}
    }
}
