package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import kotlinx.coroutines.flow.Flow

/**
 * 모든 저장소 볼륨에서 이름으로 항목을 찾고, 찾은 항목이 들어 있는 폴더로 이동한다.
 * @param repo 루트 아래를 훑어 이름이 맞는 항목을 찾는 FileSearchRepository 구현체
 * @param storage 검색할 저장소 볼륨 목록을 제공하는 StorageVolumeRepository 구현체
 * @param nav 찾은 항목의 상위 폴더를 구하고 그리로 이동하는 FileNavigatorRepository 구현체
 */
class FileSearchUseCase(
    private val repo: FileSearchRepository,
    private val storage: StorageVolumeRepository,
    private val nav: FileNavigatorRepository
) {

    /**
     * 지금 알려진 모든 저장소 볼륨 아래에서 이름이 [query]와 맞는 항목을 찾는다. 볼륨의 루트 자신은 결과에 넣지 않는다.
     * @param query 검색어
     * @return 찾는 대로 내보내는 결과 묶음 Flow. 모두 훑으면 완료된다.
     * 검색할 저장소 볼륨이 없으면 null — 아무것도 훑지 않은 것이 결과 없음으로 보이지 않게 한다
     */
    fun search(query: SearchQuery): Flow<SearchBatch>? {
        val roots = storage.storageList.value.map { it.path }.distinct()
        if (roots.isEmpty()) return null
        return repo.search(roots, query)
    }

    /**
     * [item]이 들어 있는 폴더의 경로를 구한다.
     * @param item 검색으로 찾은 항목
     * @return 상위 폴더의 경로. 상위가 없으면 null
     */
    fun locationOf(item: FileHolderItem): String? = nav.getParentPath(item.path)

    /**
     * [item]이 들어 있는 폴더로 이동한다.
     * @param item 검색으로 찾은 항목
     * @return Unit
     */
    suspend fun openLocation(item: FileHolderItem) {
        locationOf(item)?.let { nav.navigate(it) }
    }
}
