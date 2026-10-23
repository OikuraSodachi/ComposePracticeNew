package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState

/** Contract for performing file mutations (zip, copy, rename, delete, move). Every operation reports failure as an [IoResult.Failure], never as an exception. */
interface FileActionRepository {

    // Compresses [targetFiles] into a new zip file at [zipFile], reporting progress via [onProgress].
    // Never overwrites: if [zipFile] already exists, a numbered name such as "name (1).zip" is used instead.
    suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Copies [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Renames the file at [targetFile] to [newName].
    // If another entry already has [newName]: fails unless [overwrite]. With [overwrite], a file replaces a file and a folder
    // is merged into a folder (clashing entries replaced, others kept); a file/folder type mismatch always fails.
    suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean = false): IoResult<Unit>

    // Deletes the file or directory tree at [targetFile], reporting progress via [onProgress].
    // Keeps deleting the remaining entries when one fails, then reports once (PartiallyFailed) naming the entries that could not be deleted.
    suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Moves [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Extracts [zipFiles] into [destPath], reporting progress via [onProgress].
    // If [unzipHere] is true, extracts directly into [destPath] without creating a subfolder.
    suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean = false, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Creates a new directory named [name] under [parentPath].
    suspend fun makeDirectory(parentPath: String, name: String): IoResult<Unit>
}
