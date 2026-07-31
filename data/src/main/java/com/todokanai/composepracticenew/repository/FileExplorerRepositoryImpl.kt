package com.todokanai.composepracticenew.repository

import android.os.Environment
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Holds and manages the active directory navigation state (path, breadcrumb, file list). */
@Singleton
class FileExplorerRepositoryImpl @Inject constructor(
    private val converter: DataConverter,
    private val dsRepo: DataStoreRepository
) : StorageRepository(), FileNavigatorRepository {

    private val defaultStorage = Environment.getExternalStorageDirectory()

    private val _currentDirectory = MutableStateFlow(defaultStorage.absolutePath)
    override val currentDirectory: StateFlow<String> get() = _currentDirectory

    private val _fileHolderItemList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    override val fileHolderItemList: StateFlow<List<FileHolderItem>> get() = _fileHolderItemList

    init {
        navigateTo(defaultStorage.absolutePath)
    }

    override fun navigateTo(path: String?) {
        super.navigateTo(path)
        path?.let { _currentDirectory.value = it }
    }

    override suspend fun listFiles(path: String): List<FileEntry> =
        File(path).listFiles()?.map { file ->
            FileEntry(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified()
            )
        } ?: emptyList()

    override suspend fun setCurrentPath(file: File) {
        file.listFiles()?.let {
            navigateTo(file.absolutePath)
            setFileHolderItemList(dsRepo.sortBy())
        }
    }

    override fun setFileHolderItemList(sortMode: String) {
        File(currentDirectory.value).listFiles()?.let { files ->
            _fileHolderItemList.value = converter.fileHolderItemList(files, sortMode)
        }
    }
}
