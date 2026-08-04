package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** 파일 탐색기의 네비게이션 상태를 읽고 조작하는 UseCase. */
class FileNavigatorUseCase(private val nav: FileNavigatorRepository) {
    val fileList: StateFlow<List<FileHolderItem>> = nav.fileHolderItemList
    val dirTree: Flow<List<FileEntry>> = nav.dirTree
    val currentPath: StateFlow<String?> = nav.currentPath

    // stub — not yet implemented: setLocalPath, setRemotePath 분기점
    suspend fun setPath(path: String, isRemoteStorage: Boolean) {}

    suspend fun setLocalPath(file: File) = nav.setCurrentPath(file.absolutePath)
    suspend fun setRemotePath(item: RemoteStorageItem) = nav.setRemotePath(item)
    fun refresh() = nav.refresh()
}
