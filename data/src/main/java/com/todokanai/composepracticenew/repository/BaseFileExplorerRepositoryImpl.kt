package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.StorageRepository
import java.io.File
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

/** currentPath·sortBy·refreshTrigger를 결합해 fileHolderItemList를 구성하는 공통 Flow 로직을 보유한 기반 구현체. */
abstract class BaseFileExplorerRepositoryImpl(
    private val converter: DataConverter,
    private val dsRepo: DataStoreRepository,
    started: SharingStarted,
    initialPath: String? = null
) : StorageRepository(), FileNavigatorRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    @Suppress("OPT_IN_USAGE")
    override val fileHolderItemList: StateFlow<List<FileHolderItem>> =
        combine(currentPath, dsRepo.sortBy, _refreshTrigger) { path, sortMode, _ -> path to sortMode }
            .flatMapLatest { (path, sortMode) ->
                flow { emit(converter.fileHolderItemList(path?.let { listFiles(it) } ?: emptyList(), sortMode)) }
            }
            .stateIn(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                started = started,
                initialValue = emptyList()
            )

    init { initialPath?.let { navigateTo(it) } }

    override fun refresh() { _refreshTrigger.value = System.currentTimeMillis() }

    /** java.io.File API로 path의 자식 목록을 읽어 FileEntry 리스트로 변환한다. */
    protected fun localListFiles(path: String): List<FileEntry> =
        File(path).listFiles()?.map { file ->
            FileEntry(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified()
            )
        } ?: emptyList()
}
