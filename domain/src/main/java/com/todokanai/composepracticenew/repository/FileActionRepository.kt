package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.Flow

/** Contract for performing file mutations (zip, copy, rename, delete, move). */
interface FileActionRepository {

    // Compresses [targetFiles] into a new zip file at [zipFile], reporting progress via [onProgress].
    // TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
    // TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
    //   전환 전: 실패는 ProgressState.error 인밴드 방출로 전달 — IoOperation은 onCompletion()에서 처리(의도된 동작)
    //   전환 후: 실패 시 예외를 throw해 IoOperation.onError()로 라우팅
    fun zipAction(targetFiles: List<String>, zipFile: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>

    // Copies [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    // TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
    // TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
    //   전환 전: 실패는 ProgressState.error 인밴드 방출로 전달 — IoOperation은 onCompletion()에서 처리(의도된 동작)
    //   전환 후: 실패 시 예외를 throw해 IoOperation.onError()로 라우팅
    fun copyAction(targetFiles: List<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>

    // Renames the file at [targetFile] to [newName], emitting progress.
    fun renameFile(targetFile: String, newName: String): Flow<ProgressState>

    // Deletes the file at [targetFile], reporting progress via [onProgress].
    // TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
    // TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
    //   전환 전: 실패는 ProgressState.error 인밴드 방출로 전달 — IoOperation은 onCompletion()에서 처리(의도된 동작)
    //   전환 후: 실패 시 예외를 throw해 IoOperation.onError()로 라우팅
    fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>

    // Moves [targetFiles] into the directory at [targetPath], reporting progress via [onProgress].
    // TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
    // TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
    //   전환 전: 실패는 ProgressState.error 인밴드 방출로 전달 — IoOperation은 onCompletion()에서 처리(의도된 동작)
    //   전환 후: 실패 시 예외를 throw해 IoOperation.onError()로 라우팅
    fun moveFile(targetFiles: List<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>

    // Extracts [zipFiles] into [destPath], reporting progress via [onProgress].
    // If [unzipHere] is true, extracts directly into [destPath] without creating a subfolder.
    // TODO(IoOperation): onProgress 콜백 제거 예정 — IoOperation.mainOperation()이 progressState를 직접 갱신함
    // TODO(IoOperation): Flow<ProgressState> → suspend fun 전환 예정
    //   전환 전: 실패는 ProgressState.error 인밴드 방출로 전달 — IoOperation은 onCompletion()에서 처리(의도된 동작)
    //   전환 후: 실패 시 예외를 throw해 IoOperation.onError()로 라우팅
    fun unzipAction(zipFiles: List<String>, destPath: String, unzipHere: Boolean = false, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState>

    // Creates a new directory named [name] under [parentPath], emitting progress.
    fun makeDirectory(parentPath: String, name: String): Flow<ProgressState>
}
