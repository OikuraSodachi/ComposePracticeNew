package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState

/**
 * 원격 스토리지와 로컬 파일시스템 사이의 파일 전송 계약.
 * 실패는 결과로 알린다. 연결이 없거나 전송 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 */
interface RemoteFileTransferRepository {

    /**
     * remotePath의 파일 또는 디렉터리를 localPath로 다운로드한다.
     * 디렉터리는 일부 파일이 실패해도 나머지를 끝까지 받은 뒤 실패한 파일을 모아 알린다. 받다 실패한 로컬 파일은 남겨 둔다.
     * @param remotePath 다운로드할 원격 파일 또는 디렉터리의 절대 경로
     * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
     * @param isDirectory remotePath가 디렉터리인 경우 true
     * @param onProgress 전송 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean = false, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    /**
     * localPath의 파일 또는 디렉터리를 remotePath로 업로드한다.
     * 디렉터리는 일부 파일이 실패해도 나머지를 끝까지 올린 뒤 실패한 파일을 모아 알린다.
     * @param localPath 업로드할 로컬 파일 또는 디렉터리의 절대 경로
     * @param remotePath 저장될 원격 파일 또는 디렉터리의 절대 경로
     * @param onProgress 전송 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>
}
