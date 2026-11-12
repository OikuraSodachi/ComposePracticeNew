package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState

/**
 * 원격 스토리지와 로컬 파일시스템 사이의 파일 전송 계약.
 * 목적지는 디렉터리와 이름으로 받고, 그 안의 경로는 구현이 각 저장소의 경로 규칙으로 만든다.
 * 실패는 결과로 알린다. 연결이 없거나 전송 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * 작업이 취소되면 onProgress가 CancellationException을 던진다. 구현은 그것을 잡아 결과로 바꾸지 않고, 쓰다 만 파일을 지운 뒤 전파한다(이미 다 보낸 파일은 남긴다).
 */
interface RemoteFileTransferRepository {

    /**
     * remotePath의 파일 또는 디렉터리를 localDirPath 아래 name으로 다운로드한다.
     * 디렉터리는 일부 파일이 실패해도 나머지를 끝까지 받은 뒤 실패한 파일을 모아 알린다. 받다 실패한 로컬 파일은 남겨 둔다.
     * @param remotePath 다운로드할 원격 파일 또는 디렉터리의 절대 경로
     * @param localDirPath 저장할 로컬 디렉터리의 절대 경로
     * @param name 저장할 파일 또는 디렉터리의 이름
     * @param isDirectory remotePath가 디렉터리인 경우 true
     * @param onProgress 전송 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun download(remotePath: String, localDirPath: String, name: String, isDirectory: Boolean = false, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    /**
     * localPath의 파일 또는 디렉터리를 remoteDirPath 아래 name으로 업로드한다.
     * 디렉터리는 일부 파일이 실패해도 나머지를 끝까지 올린 뒤 실패한 파일을 모아 알린다.
     * @param localPath 업로드할 로컬 파일 또는 디렉터리의 절대 경로
     * @param remoteDirPath 저장할 원격 디렉터리의 절대 경로
     * @param name 저장할 파일 또는 디렉터리의 이름
     * @param onProgress 전송 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun upload(localPath: String, remoteDirPath: String, name: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>
}
