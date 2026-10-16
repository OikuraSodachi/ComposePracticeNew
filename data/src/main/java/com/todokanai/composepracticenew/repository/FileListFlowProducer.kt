package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * currentPath·sortBy·refreshTrigger 세 Flow를 결합해 fileHolderItemList StateFlow를 생성하는 팩토리.
 * @param converter 파일 목록을 UI 모델로 변환하는 DataConverter
 * @param dsRepo 정렬 기준을 읽는 DataStoreRepository
 */
class FileListFlowProducer(
    private val converter: DataConverter,
    private val dsRepo: DataStoreRepository
) {
    @Suppress("OPT_IN_USAGE")
    fun produce(
        currentPath: StateFlow<String?>,
        refreshTrigger: StateFlow<Long>,
        appScope: CoroutineScope,
        started: SharingStarted,
        listFiles: suspend (String) -> List<FileEntry>
    ): StateFlow<List<FileHolderItem>> =
        combine(currentPath, dsRepo.sortBy, refreshTrigger) { path, sortMode, _ -> path to sortMode }
            .flatMapLatest { (path, sortMode) ->
                flow { emit(converter.fileHolderItemList(path?.let { listFiles(it) } ?: emptyList(), sortMode)) }
            }
            .stateIn(scope = appScope, started = started, initialValue = emptyList())
}
