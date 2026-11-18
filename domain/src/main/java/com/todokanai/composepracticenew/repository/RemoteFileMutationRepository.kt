package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteSession

/**
 * 원격 스토리지의 파일·디렉터리 변경(이름변경·삭제·생성) 계약.
 * 새 이름은 이름으로 받고, 그 이름의 경로는 구현이 저장소의 경로 규칙으로 만든다.
 * 작업은 실행을 요청할 때 묶은 세션([RemoteSession])에서만 한다(#436). 진행 표시가 있는 삭제는 그 서버에 자기 연결을 열어 쓰고,
 * 이름 변경·폴더 생성은 화면의 연결이 그 세션일 때만 그 연결로 한다 — 그 사이 다른 서버에 접속했거나 끊겼으면 실행하지 않고 [IoResult.ConnectionLost]를 돌려준다.
 * 실패는 결과로 알린다. 연결이 없거나 작업 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 */
interface RemoteFileMutationRepository {

    /**
     * [path]의 파일 또는 디렉토리를 같은 디렉터리 안에서 [newName]으로 이름 변경한다.
     * @param session 실행을 요청할 때 묶은 세션
     * @param path 이름을 바꿀 항목의 경로
     * @param newName 새 이름(경로가 아닌 이름만)
     */
    suspend fun rename(session: RemoteSession, path: String, newName: String): IoResult<Unit>

    /**
     * [items]의 파일과 디렉터리(모든 하위 항목 포함)를 하나의 작업으로 삭제한다. 일부가 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 알린다.
     * 대상을 한 번에 받는 것은 진행을 작업 전체 기준으로 보고하기 위해서다 — 디렉터리 안쪽 항목 수는 구현만 알 수 있다.
     * @param session 실행을 요청할 때 묶은 세션. 이 세션의 서버에서 지운다
     * @param items 삭제할 원격 파일·디렉터리 항목
     * @param onProgress 진행 상태를 수신하는 콜백. 하위 항목을 훑는 동안에는 준비 단계([IndeterminatePhase.Preparing])를, 지우는 동안에는 작업 전체의 항목 수 기준 진행을 보고한다
     */
    suspend fun delete(session: RemoteSession, items: List<FileHolderItem>, onProgress: (ProgressState) -> Unit): IoResult<Unit>

    /**
     * [parentPath] 아래에 [name] 이름의 새 디렉토리를 생성한다.
     * @param session 실행을 요청할 때 묶은 세션
     * @param parentPath 새 디렉터리를 만들 부모 경로
     * @param name 새 디렉터리 이름
     */
    suspend fun makeDirectory(session: RemoteSession, parentPath: String, name: String): IoResult<Unit>
}
