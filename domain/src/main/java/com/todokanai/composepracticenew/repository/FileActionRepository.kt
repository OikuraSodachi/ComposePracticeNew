package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.Flow

/** Contract for performing file mutations (zip, copy, rename, delete, move). */
interface FileActionRepository {

    // Compresses [targetFiles] into a new zip file at [zipFile], emitting progress.
    fun zipAction(targetFiles: List<String>, zipFile: String): Flow<ProgressState>

    // Copies [targetFiles] into the directory at [targetPath], emitting progress.
    fun copyAction(targetFiles: List<String>, targetPath: String): Flow<ProgressState>

    // Renames the file at [targetFile] to [newName], emitting progress.
    fun renameFile(targetFile: String, newName: String): Flow<ProgressState>

    // Deletes the file at [targetFile], emitting progress.
    fun deleteFile(targetFile: String): Flow<ProgressState>

    // Moves [targetFiles] into the directory at [targetPath], emitting cumulative progress.
    fun moveFile(targetFiles: List<String>, targetPath: String): Flow<ProgressState>

    // Extracts [zipFiles] into [destPath], emitting cumulative progress.
    // If [unzipHere] is true, extracts directly into [destPath] without creating a subfolder.
    fun unzipAction(zipFiles: List<String>, destPath: String, unzipHere: Boolean = false): Flow<ProgressState>

    // Creates a new directory named [name] under [parentPath], emitting progress.
    fun makeDirectory(parentPath: String, name: String): Flow<ProgressState>
}
