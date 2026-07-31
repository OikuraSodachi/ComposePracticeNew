package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileHolderItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Contract for reading and updating the active directory navigation state. */
interface FileNavigatorRepository {
    val currentPath: StateFlow<File>
    val dirTree: StateFlow<List<File>>
    val fileHolderItemList: StateFlow<List<FileHolderItem>>
    suspend fun setCurrentPath(file: File)
    fun setFileHolderItemList(sortMode: String)
}
