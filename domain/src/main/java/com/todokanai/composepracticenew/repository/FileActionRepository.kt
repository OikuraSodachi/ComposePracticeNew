package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState

/** Contract for performing file mutations (compress, copy, rename, delete, move). Every operation reports failure as an [IoResult.Failure], never as an exception. */
interface FileActionRepository {

    // Compresses [targetFiles] into a new [format] archive named [baseName] + [ArchiveFormat.extension] under [parentPath], reporting progress via [onProgress].
    // Never overwrites: if that name already exists, a numbered name such as "name (1).zip" is used instead.
    // Fails without creating anything if [format] is not in [supportedArchiveFormats].
    suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Returns the archive formats [compressAction] can create.
    fun supportedArchiveFormats(): Set<ArchiveFormat>

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

    // Extracts [zipFiles] into [destPath], placing the entries as [layout] says, reporting progress via [onProgress].
    suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Returns the names that extracting [zipFile] into [destPath] with [layout] creates (or overwrites) directly under [destPath]:
    // the archive folder for [ExtractLayout.IntoArchiveFolder], the archive's top-level entries for [ExtractLayout.IntoDestination].
    // [unzipAction] places entries by the same rule, so a conflict check on these names covers everything the extraction can overwrite there.
    suspend fun extractedNames(zipFile: String, destPath: String, layout: ExtractLayout): IoResult<Set<String>>

    // Returns true if [unzipAction] supports the archive format of [path].
    fun canExtract(path: String): Boolean

    // Creates a new directory named [name] under [parentPath].
    suspend fun makeDirectory(parentPath: String, name: String): IoResult<Unit>
}
