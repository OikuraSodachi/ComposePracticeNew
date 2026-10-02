package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState

/** Contract for performing file mutations (zip, copy, rename, delete, move). */
interface FileActionRepository {

    // Compresses [targetFiles] into a new zip file at [zipFile], reporting progress via [onProgress].
    suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit = {})

    // Copies [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {})

    // Renames the file at [targetFile] to [newName].
    suspend fun renameFile(targetFile: String, newName: String)

    // Deletes the file at [targetFile], reporting progress via [onProgress].
    suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit = {})

    // Moves [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {})

    // Extracts [zipFiles] into [destPath], reporting progress via [onProgress].
    // If [unzipHere] is true, extracts directly into [destPath] without creating a subfolder.
    suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean = false, onProgress: (ProgressState) -> Unit = {})

    // Creates a new directory named [name] under [parentPath].
    suspend fun makeDirectory(parentPath: String, name: String)
}
