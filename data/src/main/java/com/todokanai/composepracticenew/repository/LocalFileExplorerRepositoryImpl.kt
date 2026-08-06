package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** 로컬 파일 시스템만 탐색하는 FileNavigatorRepository 구현체. FTP 의존성이 없다. */
class LocalFileExplorerRepositoryImpl(
    private val converter: DataConverter,
    private val dsRepo: DataStoreRepository,
    private val initialPath: String? = null
) : StorageRepository(), FileNavigatorRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    @Suppress("OPT_IN_USAGE")
    override val fileHolderItemList: StateFlow<List<FileHolderItem>> =
        combine(currentPath, dsRepo.sortBy, _refreshTrigger) { path, sortMode, _ -> path to sortMode }
            .flatMapLatest { (path, sortMode) ->
                flow {
                    val files = path?.let { listFiles(it) } ?: emptyList()
                    emit(converter.fileHolderItemList(files, sortMode))
                }
            }
            .stateIn(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    init {
        initialPath?.let { navigateTo(it) }
    }

    override fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
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

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) = setLocalPath(path)

    override suspend fun setLocalPath(path: String) {
        if (File(path).listFiles() != null) navigateTo(path)
    }

    // stub — not yet implemented
    override suspend fun connectRemote(address: String, port: Int, userId: String, password: String): Boolean = false

    // stub — not yet implemented
    override suspend fun setRemotePath(path: String) {}
}
