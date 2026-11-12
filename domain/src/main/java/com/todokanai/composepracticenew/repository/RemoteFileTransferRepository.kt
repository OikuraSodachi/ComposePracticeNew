package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileHolderItem
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
     * 원격 항목 [items]를 차례로 localDirPath 아래 각자의 이름으로 다운로드한다. 진행률은 모든 항목을 합쳐 보고한다.
     * 일부 파일이 실패해도 나머지를 끝까지 받은 뒤 실패한 파일을 모아 알린다(연결이 끊기면 멈춘다). 받다 실패한 로컬 파일은 남겨 둔다.
     * 로컬 항목은 덮어쓰지 않는 쓰기로 먼저 만들고, 이름이 이미 있다고 파일 시스템이 알릴 때만 [resolveConflict]에 묻는다
     * (파일은 덮어쓰기, 폴더는 합치기, 종류가 엇갈리면 건너뛰기만). 묻는 동안 원격 세션을 붙잡지 않는다.
     * @param items 다운로드할 원격 파일 또는 디렉터리 항목
     * @param localDirPath 저장할 로컬 디렉터리의 절대 경로
     * @param resolveConflict 같은 이름의 로컬 항목을 만났을 때 처리 방법을 묻는 통로
     * @param onProgress 전송 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun download(items: List<FileHolderItem>, localDirPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    /**
     * localPath의 파일 또는 디렉터리를 remoteDirPath 아래 name으로 업로드한다.
     * 디렉터리는 일부 파일이 실패해도 나머지를 끝까지 올린 뒤 실패한 파일을 모아 알린다.
     * 같은 이름의 원격 파일은 묻지 않고 덮어쓴다 — FTP에는 "지정한 이름으로, 없을 때만 만들기"를 보장하는 표준 명령이 없어
     * 겹침은 호출부가 확인할 때의 목록으로 판정하며, 그 뒤에 서버에 생긴 같은 이름의 파일을 덮어쓰는 틈은 지원 범위 밖이다.
     * @param localPath 업로드할 로컬 파일 또는 디렉터리의 절대 경로
     * @param remoteDirPath 저장할 원격 디렉터리의 절대 경로
     * @param name 저장할 파일 또는 디렉터리의 이름
     * @param onProgress 전송 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun upload(localPath: String, remoteDirPath: String, name: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>
}
