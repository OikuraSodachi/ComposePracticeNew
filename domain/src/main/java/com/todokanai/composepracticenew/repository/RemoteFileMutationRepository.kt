package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteConnectionLostException

/**
 * 원격 스토리지의 파일·디렉터리 변경(이름변경·삭제·생성) 계약.
 * 실패는 예외로 알린다. 연결이 없거나 작업 중 끊기면 [RemoteConnectionLostException]을 던진다.
 */
interface RemoteFileMutationRepository {

    /** fromPath의 파일 또는 디렉토리를 toPath로 이름 변경 또는 이동한다. */
    suspend fun rename(fromPath: String, toPath: String)

    /** path의 파일을 삭제한다. */
    suspend fun deleteFile(path: String)

    /** path에 새 디렉토리를 생성한다. */
    suspend fun makeDirectory(path: String)

    /** path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다. 일부 하위 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 알린다. */
    suspend fun removeDirectoryRecursive(path: String)
}
