package com.todokanai.composepracticenew.repository

import android.os.Environment
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.tools.independent.dirTree_td
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Holds and manages the active directory navigation state (path, breadcrumb, file list). */
@Singleton
class FileNavigator @Inject constructor(
    private val converter: DataConverter,
    private val dsRepo: DataStoreRepository
) : FileNavigatorRepository {
    private val defaultStorage = Environment.getExternalStorageDirectory()

    private val _currentPath = MutableStateFlow<File>(defaultStorage)
    override val currentPath: StateFlow<File> get() = _currentPath

    private val _dirTree = MutableStateFlow<List<File>>(dirTree_td(defaultStorage))
    override val dirTree: StateFlow<List<File>> get() = _dirTree

    private val _fileHolderItemList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    override val fileHolderItemList: StateFlow<List<FileHolderItem>> get() = _fileHolderItemList

    override suspend fun setCurrentPath(file: File) {
        file.listFiles()?.let {
            _currentPath.value = file
            _dirTree.value = dirTree_td(file)
            setFileHolderItemList(dsRepo.sortBy())
        }
    }

    override fun setFileHolderItemList(sortMode: String) {
        _currentPath.value.listFiles()?.let { files ->
            _fileHolderItemList.value = converter.fileHolderItemList(files, sortMode)
        }
    }
}
