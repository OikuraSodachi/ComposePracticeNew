package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.Flow

/** 원격 스토리지와 로컬 파일시스템 사이의 파일 전송 계약. */
interface RemoteFileTransferRepository {

    /**
     * remotePath의 파일 또는 디렉터리를 localPath로 다운로드한다.
     * @param remotePath 다운로드할 원격 파일 또는 디렉터리의 절대 경로
     * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
     * @param isDirectory remotePath가 디렉터리인 경우 true
     * @returns 전송 진행 상태를 방출하는 Flow
     * TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
     * TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
     */
    fun download(remotePath: String, localPath: String, isDirectory: Boolean = false, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>

    /**
     * localPath의 파일을 remotePath로 업로드한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remotePath 저장될 원격 파일의 절대 경로
     * @returns 전송 진행 상태를 방출하는 Flow
     * TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
     * TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
     */
    fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>
}
