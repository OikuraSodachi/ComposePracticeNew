package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.FileActionRepository
import kotlinx.coroutines.flow.Flow

/** Delegates all file mutation operations to [FileActionRepository]. */
class FileActionUseCase(private val repository: FileActionRepository) {

    // @param targetFiles absolutePath (or URI) of files to compress
    // @param zipFile absolutePath (or URI) of new zip file to create
    fun zipAction(targetFiles: List<String>, zipFile: String): Flow<ProgressState> =
        repository.zipAction(targetFiles, zipFile)

    // @param targetFiles absolutePath (or URI) of files to copy
    // @param targetPath absolutePath (or URI) of destination directory
    fun copyAction(targetFiles: List<String>, targetPath: String): Flow<ProgressState> =
        repository.copyAction(targetFiles, targetPath)

    // @param targetFile absolutePath (or URI) of file to rename
    // @param newName new file name (not a full path)
    fun renameFile(targetFile: String, newName: String): Flow<ProgressState> =
        repository.renameFile(targetFile, newName)

    // @param targetFile absolutePath (or URI) of file or directory to delete
    fun deleteFile(targetFile: String): Flow<ProgressState> =
        repository.deleteFile(targetFile)

    // @param targetFile absolutePath (or URI) of file to move
    // @param targetPath absolutePath (or URI) of destination directory
    fun moveFile(targetFile: String, targetPath: String): Flow<ProgressState> =
        repository.moveFile(targetFile, targetPath)
}