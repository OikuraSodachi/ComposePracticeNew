package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

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

    companion object {
        /**
         * 검색 하나가 내놓는 결과의 상한. 이보다 많이 맞으면 결과가 메모리를 끝없이 차지하지 않도록 검색을 멈춘다 —
         * 사람이 훑어볼 수 있는 양을 넘으므로 검색어를 좁히는 편이 낫다
         */
        const val MAX_RESULTS = 1_000
    }

    /**
     * 지금 알려진 모든 저장소 볼륨 아래에서 이름이 [query]와 맞는 항목을 찾는다. 볼륨의 루트 자신은 결과에 넣지 않는다.
     * 맞는 항목이 [MAX_RESULTS]를 넘으면 상한까지만 내보내고 훑기를 멈춘다.
     * @param query 검색어
     * @return 찾는 대로 내보내는 결과 묶음 Flow. 모두 훑으면 완료된다. 상한을 넘으면 [SearchBatch.limitReached] 묶음을 마지막으로 내보내고 완료된다.
     * 검색할 저장소 볼륨이 없으면 null — 아무것도 훑지 않은 것이 결과 없음으로 보이지 않게 한다
     */
    fun search(query: SearchQuery): Flow<SearchBatch>? {
        val roots = storage.storageList.value.map { it.path }.distinct()
        if (roots.isEmpty()) return null
        return repo.search(roots, query).limitedTo(MAX_RESULTS)
    }

    /**
     * [item]이 들어 있는 폴더의 경로를 구한다.
     * @param item 검색으로 찾은 항목
     * @return 상위 폴더의 경로. 상위가 없으면 null
     */
    fun locationOf(item: FileHolderItem): String? = nav.getParentPath(item.path)

    /**
     * 검색으로 찾은 폴더 [folder]로 이동한다. 찾은 뒤 지워졌거나 처음부터 읽지 못한 폴더면 이동하지 않는다.
     * @param folder 검색으로 찾은 폴더
     * @return 이동했는지, 거부됐는지
     */
    suspend fun openFolder(folder: FileHolderItem): NavigateResult = nav.navigate(folder.path)

    /**
     * [item]이 들어 있는 폴더로 이동한다.
     * @param item 검색으로 찾은 항목
     * @return 이동했는지, 거부됐는지. 상위 폴더가 없으면 갈 곳이 없으므로 거부다
     */
    suspend fun openLocation(item: FileHolderItem): NavigateResult =
        locationOf(item)?.let { nav.navigate(it) } ?: NavigateResult.Rejected

    /**
     * 찾은 항목이 모두 [max]개까지만 지나가게 한다. 상한을 넘는 항목이 오면 넘는 부분을 버린 묶음을 상한 도달로 표시해 내보내고,
     * 수집을 끝내 위쪽(저장소 훑기)을 취소한다. 맞는 항목이 정확히 [max]개면 상한을 넘지 않았으므로 끝까지 훑는다.
     * @param max 지나가게 할 찾은 항목의 최대 수
     * @return 상한을 적용한 결과 묶음 Flow
     */
    private fun Flow<SearchBatch>.limitedTo(max: Int): Flow<SearchBatch> = flow {
        // 남은 수는 수집마다 새로 센다 — 같은 Flow를 다시 수집해도 이전 수집의 수가 이어지지 않도록
        var remaining = max
        emitAll(this@limitedTo.transformWhile { batch ->
            val overflow = batch.found.size > remaining
            val kept = if (overflow) batch.found.take(remaining) else batch.found
            remaining -= kept.size
            emit(SearchBatch(kept, batch.unreadable, limitReached = overflow))
            !overflow
        })
    }
}
