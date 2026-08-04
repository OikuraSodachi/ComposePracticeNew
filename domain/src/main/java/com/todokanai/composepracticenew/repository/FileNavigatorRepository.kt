package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File


/** Contract for reading and updating the active directory navigation state. */
interface FileNavigatorRepository {
    val currentPath: StateFlow<String?>
    val dirTree: Flow<List<FileEntry>>
    val fileHolderItemList: StateFlow<List<FileHolderItem>>
    suspend fun setCurrentPath(file: File)
    fun refresh()
    /** 원격 스토리지에 접속하여 해당 스토리지 루트로 탐색 위치를 이동시킨다. */
    suspend fun navigateToRemote(item: RemoteStorageItem)
}
