package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted

/**
 * 로컬 파일 시스템만 탐색하는 FileNavigatorRepository 구현체. FTP 의존성이 없다.
 * @param converter 파일 목록을 UI 모델로 변환하는 DataConverter
 * @param dsRepo 정렬 기준 등 설정을 읽는 DataStoreRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param localFiles 로컬 디렉터리 목록 읽기를 담당하는 LocalFileDataSource
 * @param initialPath 초기 탐색 경로; null이면 설정하지 않는다
 */
class LocalFileExplorerRepositoryImpl(
    converter: DataConverter,
    dsRepo: DataStoreRepository,
    appScope: CoroutineScope,
    private val localFiles: LocalFileDataSource,
    initialPath: String? = null
) : BaseFileExplorerRepository(converter, dsRepo, appScope, SharingStarted.WhileSubscribed(5_000), initialPath) {

    override suspend fun listFiles(path: String): List<FileEntry> = localFiles.listFiles(path)

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) = setLocalPath(path)

    override suspend fun setLocalPath(path: String) {
        if (localFiles.canRead(path)) navigateTo(path)
    }

}
