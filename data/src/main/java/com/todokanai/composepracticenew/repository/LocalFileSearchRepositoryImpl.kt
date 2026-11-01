package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.datasource.LocalFileSearchDataSource
import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 로컬 파일 시스템을 훑어 이름으로 항목을 찾는 FileSearchRepository 구현체.
 * @param dataSource 로컬 디렉터리 트리를 훑어 이름이 맞는 항목을 찾는 LocalFileSearchDataSource
 * @param converter 찾은 FileEntry를 domain FileHolderItem으로 바꾸는 변환기
 */
class LocalFileSearchRepositoryImpl @Inject constructor(
    private val dataSource: LocalFileSearchDataSource,
    private val converter: DataConverter
) : FileSearchRepository {

    override fun search(roots: List<String>, query: SearchQuery): Flow<SearchBatch> =
        dataSource.search(roots, query).map { SearchBatch(converter.fileHolderItemsInOrder(it.found), it.unreadable) }
}
