package com.todokanai.composepracticenew.repository

/** 원격 스토리지의 파일·디렉터리 변경(이름변경·삭제·생성) 계약. */
interface RemoteFileMutationRepository {

    /** fromPath의 파일 또는 디렉토리를 toPath로 이름 변경 또는 이동한다. 성공 여부를 반환한다. */
    suspend fun rename(fromPath: String, toPath: String): Boolean

    /** path의 파일을 삭제한다. 성공 여부를 반환한다. */
    suspend fun deleteFile(path: String): Boolean

    /** path에 새 디렉토리를 생성한다. 성공 여부를 반환한다. */
    suspend fun makeDirectory(path: String): Boolean

    /** path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다. 성공 여부를 반환한다. */
    suspend fun removeDirectoryRecursive(path: String): Boolean
}
