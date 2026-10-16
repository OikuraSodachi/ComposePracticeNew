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
    /**
     * currentPath·sortBy·refreshTrigger를 결합해 fileHolderItemList StateFlow를 반환한다.
     * @param currentPath 현재 탐색 중인 디렉터리 경로 StateFlow
     * @param refreshTrigger 새로고침 신호를 전달하는 Long 타임스탬프 StateFlow
     * @param appScope StateFlow를 공유할 애플리케이션 수명 CoroutineScope
     * @param started StateFlow 공유 시작 전략
     * @param listFiles 경로를 받아 해당 디렉터리의 FileEntry 목록을 반환하는 suspend 함수
     * @return 현재 경로의 파일 목록을 UI 모델로 변환해 방출하는 StateFlow
     */
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
