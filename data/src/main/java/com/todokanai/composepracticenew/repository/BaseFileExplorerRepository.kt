package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.fileexplorer.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

/**
 * currentPath·sortBy·refreshTrigger를 결합해 fileHolderItemList를 구성하는 공통 Flow 로직을 보유한 기반 클래스.
 * @param converter 파일 목록을 UI 모델로 변환하는 DataConverter
 * @param dsRepo 정렬 기준 등 설정을 읽는 DataStoreRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param started StateFlow 공유 시작 전략
 * @param initialPath 초기 탐색 경로; null이면 루트로 시작하지 않는다
 */
abstract class BaseFileExplorerRepository(
    converter: DataConverter,
    dsRepo: DataStoreRepository,
    private val appScope: CoroutineScope,
    started: SharingStarted,
    initialPath: String? = null
) : StorageRepository(), FileNavigatorRepository {

    private val _refreshTrigger = MutableStateFlow(0L)
    private val producer = FileListFlowProducer(converter, dsRepo)

    override val fileHolderItemList: StateFlow<List<FileHolderItem>> =
        producer.produce(currentPath, _refreshTrigger, appScope, started) { listFiles(it) }

    init { initialPath?.let { navigateTo(it) } }

    override fun refresh() { _refreshTrigger.value = System.currentTimeMillis() }
}
