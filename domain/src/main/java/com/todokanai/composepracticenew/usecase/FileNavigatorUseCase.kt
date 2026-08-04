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

    suspend fun navigateTo(file: File) = nav.setCurrentPath(file)
    suspend fun navigateToRemote(item: RemoteStorageItem) = nav.navigateToRemote(item)
    fun refresh() = nav.refresh()
}
