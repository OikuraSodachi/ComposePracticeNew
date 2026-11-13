package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState

/**
 * Contract for performing file mutations (compress, copy, rename, delete, move). Every operation reports failure as an [IoResult.Failure], never as an exception.
 * 작업이 취소되면 onProgress가 CancellationException을 던진다. 구현은 그것을 잡아 결과로 바꾸지 않고, 쓰다 만 파일(압축은 만들던 아카이브)을 지운 뒤 전파한다(이미 다 쓴 파일은 남긴다).
 */
interface FileActionRepository {

    // Compresses [targetFiles] into a new [format] archive named [baseName] + [ArchiveFormat.extension] under [parentPath], reporting progress via [onProgress].
    // Never overwrites: if that name already exists, a numbered name such as "name (1).zip" is used instead.
    // Fails without creating anything if [format] is not in [supportedArchiveFormats].
    suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Returns the archive formats [compressAction] can create.
    fun supportedArchiveFormats(): Set<ArchiveFormat>

    // Copies [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    // Never replaces an existing entry without asking: each entry is first written in a way that fails if the name is taken,
    // and only when the file system reports the name as taken is [resolveConflict] asked. PROCEED replaces a file, or merges a
    // folder into a folder (its entries are then judged one by one); a file/folder type mismatch can only be skipped.
    // SKIP leaves the existing entry (and, for a folder, everything beneath the source folder) untouched.
    suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Renames the file at [targetFile] to [newName].
    // If another entry already has [newName]: fails unless [overwrite]. With [overwrite], a file replaces a file and a folder
    // is merged into a folder (clashing entries replaced, others kept); a file/folder type mismatch always fails.
    suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean = false): IoResult<Unit>

    // Deletes the files and directory trees at [targetFiles] as one operation, reporting progress via [onProgress].
    // Takes every target at once so progress covers the whole operation — only the implementation knows how many entries a folder holds.
    // Keeps deleting the remaining entries when one fails, then reports once (PartiallyFailed) naming the entries that could not be deleted,
    // each under its target's name.
    suspend fun deleteFiles(targetFiles: Set<String>, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Moves [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    // Name clashes are resolved through [resolveConflict] exactly as in [copyAction]. Skipped entries stay at their source;
    // a merged source folder is removed only once everything in it has been moved.
    suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Extracts [zipFiles] into [destPath], placing the entries as [layout] says, reporting progress via [onProgress].
    // Folders are merged into existing folders without asking. A file entry whose name is taken is resolved through
    // [resolveConflict] (PROCEED replaces the file); an entry whose type differs from the existing one can only be skipped.
    suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit>

    // Returns true if [unzipAction] supports the archive format of [path].
    fun canExtract(path: String): Boolean

    // Creates a new directory named [name] under [parentPath].
    suspend fun makeDirectory(parentPath: String, name: String): IoResult<Unit>
}
