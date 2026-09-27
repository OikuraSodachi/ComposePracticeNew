package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.repository.FtpRepository

/**
 * FTP 업로드 작업을 IoOperation 생명주기로 실행하는 구현체.
 * @param localPath 업로드할 로컬 파일 경로
 * @param remoteDestPath 저장될 원격 파일의 절대 경로
 * @param ftpRepo FTP 파일 전송 작업을 실행하는 FtpRepository
 * @param notifier 파일 작업 알림 전송 인터페이스
 * @param instanceId 작업 인스턴스 식별자
 * @param completionMessage 작업 완료 시 표시할 메시지
 * @param onRefresh 작업 완료 후 파일 목록을 갱신하는 콜백
 * @param onEmitCompletion 완료 메시지를 UI에 전달하는 콜백
 * @param onEmitError 오류 메시지를 UI에 전달하는 콜백
 * @param setProgress 진행 상태를 업데이트하는 콜백
 * @param clearProgress 진행 상태를 초기화하는 콜백
 */
class FtpUploadOperation(
    private val localPath: String,
    private val remoteDestPath: String,
    private val ftpRepo: FtpRepository,
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
        ftpRepo.upload(localPath, remoteDestPath) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_UPLOAD)
            setProgress(progressState)
            state.progress?.let { notifier.uploadProgressNoti(it, instanceId) }
        }.collect {}
    }
}
