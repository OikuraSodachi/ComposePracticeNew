package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState

/**
 * 원격 스토리지의 파일·디렉터리 변경(이름변경·삭제·생성) 계약.
 * 새 이름은 이름으로 받고, 그 이름의 경로는 구현이 저장소의 경로 규칙으로 만든다.
 * 실패는 결과로 알린다. 연결이 없거나 작업 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 */
interface RemoteFileMutationRepository {

    /** path의 파일 또는 디렉토리를 같은 디렉터리 안에서 newName으로 이름 변경한다. */
    suspend fun rename(path: String, newName: String): IoResult<Unit>

    /**
     * [items]의 파일과 디렉터리(모든 하위 항목 포함)를 하나의 작업으로 삭제한다. 일부가 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 알린다.
     * 대상을 한 번에 받는 것은 진행을 작업 전체 기준으로 보고하기 위해서다 — 디렉터리 안쪽 항목 수는 구현만 알 수 있다.
     * @param items 삭제할 원격 파일·디렉터리 항목
     * @param onProgress 진행 상태를 수신하는 콜백. 하위 항목을 훑는 동안에는 준비 단계([ProgressState.preparing])를, 지우는 동안에는 작업 전체의 항목 수 기준 진행을 보고한다
     */
    suspend fun delete(items: List<FileHolderItem>, onProgress: (ProgressState) -> Unit): IoResult<Unit>

    /** parentPath 아래에 name 이름의 새 디렉토리를 생성한다. */
    suspend fun makeDirectory(parentPath: String, name: String): IoResult<Unit>
}
