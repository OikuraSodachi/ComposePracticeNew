package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.repository.FileActionRepository

/**
 * 로컬 압축해제 작업을 IoOperation 생명주기로 실행하는 구현체.
 * @param zipFiles 압축 해제할 zip 파일 경로 목록
 * @param destPath 압축 해제 대상 디렉터리 경로
 * @param unzipHere true이면 destPath에 직접 해제, false이면 zip 파일명 하위 폴더 생성 후 해제
 * @param fileActionRepo 파일 변경 작업을 실행하는 FileActionRepository
 * @param notifier 파일 작업 알림 전송 인터페이스
 * @param instanceId 작업 인스턴스 식별자
 * @param completionMessage 작업 완료 시 표시할 메시지
 * @param onRefresh 작업 완료 후 파일 목록을 갱신하는 콜백
 * @param onEmitCompletion 완료 메시지를 UI에 전달하는 콜백
 * @param onEmitError 오류 메시지를 UI에 전달하는 콜백
 * @param setProgress 진행 상태를 업데이트하는 콜백
 * @param clearProgress 진행 상태를 초기화하는 콜백
 */
class UnzipOperation(
    private val zipFiles: Set<String>,
    private val destPath: String,
    private val unzipHere: Boolean,
    private val fileActionRepo: FileActionRepository,
    notifier: FileOperationNotifier,
    instanceId: Int,
    completionMessage: String,
    onRefresh: suspend () -> Unit,
    onEmitCompletion: suspend (String) -> Unit,
    onEmitError: suspend (String) -> Unit,
    setProgress: (ProgressState) -> Unit,
    clearProgress: () -> Unit
) : NotiIoOperation(notifier, instanceId, completionMessage, onRefresh, onEmitCompletion, onEmitError, setProgress, clearProgress) {

    override val actionKey = ACTION_KEY_UNZIP

    override suspend fun mainOperation() {
        fileActionRepo.unzipAction(zipFiles, destPath, unzipHere) { state ->
            progressState = state.copy(actionKey = ACTION_KEY_UNZIP)
            setProgress(progressState)
            state.progress?.let { notifier.unzipProgressNoti(it, instanceId) }
        }
    }
}
