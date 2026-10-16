package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState

/**
 * 로컬 파일 시스템에서 파일 뮤테이션(복사·이동·삭제·압축·압축 해제·폴더 생성)을 수행하는 DataSource 인터페이스.
 * FileActionRepositoryImpl이 이 인터페이스를 통해 로컬 I/O에 간접 의존하므로, 구현체 교체로 스토리지 백엔드를 교체할 수 있다.
 */
interface LocalFileOperationDataSource {
    suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit)
    suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit)
    suspend fun renameFile(targetFile: String, newName: String)
    suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit)
    suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit)
    suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit)
    suspend fun makeDirectory(parentPath: String, name: String)
}
