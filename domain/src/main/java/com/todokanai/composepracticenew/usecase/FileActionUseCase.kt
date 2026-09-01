package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.FileActionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Delegates all file mutation operations to [FileActionRepository], with input validation and exception handling. */
class FileActionUseCase(private val repository: FileActionRepository) {

    // @param targetFiles absolutePath (or URI) of files to compress
    // @param zipFile absolutePath (or URI) of new zip file to create
    fun zipAction(targetFiles: List<String>, zipFile: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> =
        repository.zipAction(targetFiles, zipFile, onProgress)
            .catch { e -> emit(ProgressState(error = e.message ?: "압축 중 오류가 발생했습니다.")) }

    // @param targetFiles absolutePath (or URI) of files to copy
    // @param targetPath absolutePath (or URI) of destination directory
    fun copyAction(targetFiles: List<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> {
        // 출발지-목적지 동일 경로 검사: File(targetPath, src.name) == src 가 되는 케이스를 차단
        for (srcPath in targetFiles) {
            val srcName = srcPath.substringAfterLast('/')
            if ("$targetPath/$srcName" == srcPath) {
                return flowOf(ProgressState(error = "복사 실패: 출발지와 목적지가 동일합니다. ($srcPath)"))
            }
        }
        return repository.copyAction(targetFiles, targetPath, onProgress)
            .catch { e -> emit(ProgressState(error = e.message ?: "복사 중 오류가 발생했습니다.")) }
    }

    // @param targetFile absolutePath (or URI) of file to rename
    // @param newName new file name (not a full path)
    fun renameFile(targetFile: String, newName: String): Flow<ProgressState> =
        repository.renameFile(targetFile, newName)
            .catch { e -> emit(ProgressState(error = e.message ?: "이름 변경 중 오류가 발생했습니다.")) }

    // @param targetFile absolutePath (or URI) of file or directory to delete
    fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> =
        repository.deleteFile(targetFile, onProgress)
            .catch { e -> emit(ProgressState(error = e.message ?: "삭제 중 오류가 발생했습니다.")) }

    // @param zipFiles absolutePaths of zip files to extract
    // @param destPath absolutePath of destination directory
    // @param unzipHere true이면 [destPath]에 직접 해제, false이면 zip 파일명 하위 폴더 생성 후 해제
    fun unzipAction(zipFiles: List<String>, destPath: String, unzipHere: Boolean = false, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> =
        repository.unzipAction(zipFiles, destPath, unzipHere, onProgress)
            .catch { e -> emit(ProgressState(error = e.message ?: "압축 해제 중 오류가 발생했습니다.")) }

    // @param parentPath absolutePath (or URI) of the parent directory
    // @param name name of the new directory
    fun makeDirectory(parentPath: String, name: String): Flow<ProgressState> =
        repository.makeDirectory(parentPath, name)
            .catch { e -> emit(ProgressState(error = e.message ?: "폴더 생성 중 오류가 발생했습니다.")) }

    // @param targetFiles absolutePaths (or URIs) of files to move
    // @param targetPath absolutePath (or URI) of destination directory
    fun moveFile(targetFiles: List<String>, targetPath: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> {
        for (srcPath in targetFiles) {
            val srcName = srcPath.substringAfterLast('/')
            if ("$targetPath/$srcName" == srcPath) {
                return flowOf(ProgressState(error = "이동 실패: 출발지와 목적지가 동일합니다. ($srcPath)"))
            }
        }
        return repository.moveFile(targetFiles, targetPath, onProgress)
            .catch { e -> emit(ProgressState(error = e.message ?: "이동 중 오류가 발생했습니다.")) }
    }
}
