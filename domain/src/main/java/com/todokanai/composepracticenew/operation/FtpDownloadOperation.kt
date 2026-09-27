package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.repository.FtpRepository

/**
 * FTP 다운로드 작업을 IoOperation 생명주기로 실행하는 구현체.
 * @param remotePath 다운로드할 원격 파일 경로
 * @param localDestPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
 * @param isDirectory 디렉터리 여부
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
class FtpDownloadOperation(
    private val remotePath: String,
    private val localDestPath: String,
    private val isDirectory: Boolean,
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

    override val actionKey = ACTION_KEY_DOWNLOAD

    override suspend fun mainOperation() {
        ftpRepo.download(remotePath, localDestPath, isDirectory) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_DOWNLOAD)
            setProgress(progressState)
            state.progress?.let { notifier.downloadProgressNoti(it, instanceId) }
        }.collect {}
    }
}
