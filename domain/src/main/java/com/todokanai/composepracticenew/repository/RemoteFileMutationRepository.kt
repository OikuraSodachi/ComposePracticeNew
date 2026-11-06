package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.IoResult

/**
 * 원격 스토리지의 파일·디렉터리 변경(이름변경·삭제·생성) 계약.
 * 새 이름은 이름으로 받고, 그 이름의 경로는 구현이 저장소의 경로 규칙으로 만든다.
 * 실패는 결과로 알린다. 연결이 없거나 작업 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 */
interface RemoteFileMutationRepository {

    /** path의 파일 또는 디렉토리를 같은 디렉터리 안에서 newName으로 이름 변경한다. */
    suspend fun rename(path: String, newName: String): IoResult<Unit>

    /** path의 파일을 삭제한다. */
    suspend fun deleteFile(path: String): IoResult<Unit>

    /** parentPath 아래에 name 이름의 새 디렉토리를 생성한다. */
    suspend fun makeDirectory(parentPath: String, name: String): IoResult<Unit>

    /** path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다. 일부 하위 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 알린다. */
    suspend fun removeDirectoryRecursive(path: String): IoResult<Unit>
}
