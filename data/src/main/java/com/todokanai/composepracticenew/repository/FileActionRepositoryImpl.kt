package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalFileOperationDataSource
import com.todokanai.composepracticenew.model.ProgressState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileActionRepository를 LocalFileOperationDataSource에 위임하는 얇은 어댑터.
 * @param dataSource 실제 로컬 I/O를 수행하는 LocalFileOperationDataSource 구현체
 */
@Singleton
class FileActionRepositoryImpl @Inject constructor(
    private val dataSource: LocalFileOperationDataSource
) : FileActionRepository {
    override suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) = dataSource.zipAction(targetFiles, zipFile, onProgress)
    override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = dataSource.copyAction(targetFiles, targetPath, onProgress)
    override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = dataSource.renameFile(targetFile, newName, overwrite)
    override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = dataSource.deleteFile(targetFile, onProgress)
    override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = dataSource.moveFile(targetFiles, targetPath, onProgress)
    override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) = dataSource.unzipAction(zipFiles, destPath, unzipHere, onProgress)
    override suspend fun makeDirectory(parentPath: String, name: String) = dataSource.makeDirectory(parentPath, name)
}
