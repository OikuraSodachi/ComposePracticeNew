package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.usecase.FileSearchUseCase
import com.todokanai.composepracticenew.usecase.FileSearchUseCase.Companion.MAX_RESULTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [FileSearchUseCase.search]가 찾은 항목을 [MAX_RESULTS]개까지만 내보내고, 넘으면 상한 도달을 표시한 묶음을 마지막으로 훑기를 멈추며,
 * 정확히 상한만큼 맞으면 멈추지 않고 끝까지 훑는지 검증하는 단위 테스트.
 */
class FileSearchUseCaseTest {

    /** 테스트마다 쓰는 검색어. 저장소 가짜는 검색어를 보지 않는다. */
    private val query = SearchQuery.of("a")!!

    @Test
    fun `상한을 넘으면 상한까지만 내보내고 상한 도달로 끝내며 그 뒤는 훑지 않는다`() = runBlocking {
        var walkedPastLimit = false
        val batches = searchWith {
            emit(SearchBatch(items(MAX_RESULTS - 1, "first"), emptyList()))
            emit(SearchBatch(items(2, "second"), emptyList()))
            walkedPastLimit = true
            emit(SearchBatch(items(1, "third"), emptyList()))
        }

        assertEquals(listOf(MAX_RESULTS - 1, 1), batches.map { it.found.size })
        assertEquals("second0", batches.last().found.single().name)
        assertEquals(listOf(false, true), batches.map { it.limitReached })
        assertFalse(walkedPastLimit)
    }

    @Test
    fun `정확히 상한만큼 맞으면 상한 도달이 아니며 끝까지 훑는다`() = runBlocking {
        val failed = FailedItem("/storage/0/Android/data", null)
        val batches = searchWith {
            emit(SearchBatch(items(MAX_RESULTS, "all"), emptyList()))
            emit(SearchBatch(emptyList(), listOf(failed)))
        }

        assertEquals(listOf(MAX_RESULTS, 0), batches.map { it.found.size })
        assertEquals(listOf(false, false), batches.map { it.limitReached })
        assertEquals(listOf(failed), batches.last().unreadable)
    }

    @Test
    fun `상한에 닿은 뒤 하나라도 더 찾으면 그 묶음의 항목은 버리고 읽지 못한 항목과 함께 상한 도달을 알린다`() = runBlocking {
        val failed = FailedItem("/storage/0/b", null)
        val batches = searchWith {
            emit(SearchBatch(items(MAX_RESULTS, "all"), emptyList()))
            emit(SearchBatch(items(1, "over"), listOf(failed)))
        }

        assertEquals(SearchBatch(emptyList(), listOf(failed), limitReached = true), batches.last())
    }

    @Test
    fun `같은 검색 Flow를 다시 수집하면 상한을 처음부터 센다`() = runBlocking {
        val search = useCaseWith { emit(SearchBatch(items(MAX_RESULTS, "once"), emptyList())) }.search(query)!!

        search.toList()
        assertEquals(listOf(false), search.toList().map { it.limitReached })
    }

    /** [walk]로 결과 묶음을 내보내는 저장소로 검색해 받은 묶음을 모두 돌려준다. */
    private suspend fun searchWith(walk: suspend FlowCollector<SearchBatch>.() -> Unit): List<SearchBatch> =
        useCaseWith(walk).search(query)!!.toList()

    /** 수집할 때마다 [walk]로 결과 묶음을 내보내는 저장소와 저장소 볼륨 하나로 만든 UseCase. */
    private fun useCaseWith(walk: suspend FlowCollector<SearchBatch>.() -> Unit): FileSearchUseCase {
        val repo = object : FileSearchRepository {
            override fun search(roots: List<String>, query: SearchQuery): Flow<SearchBatch> = flow(walk)
        }
        val storage = object : StorageVolumeRepository {
            override val storageList = MutableStateFlow(listOf(StorageVolumeInfo("/storage/0", 0, 0)))
            override fun load() {}
        }
        return FileSearchUseCase(repo, storage, NoOpNavigator())
    }

    /** 이름이 [prefix]로 시작하는 파일 [count]개. */
    private fun items(count: Int, prefix: String): List<FileHolderItem> = List(count) {
        FileHolderItem(path = "/storage/0/$prefix$it", isDirectory = false, name = "$prefix$it", sizeBytes = 0, lastModifiedMillis = 0L)
    }

    /** 검색 테스트에서 쓰지 않는 탐색 저장소. */
    private class NoOpNavigator : FileNavigatorRepository {
        override val currentPath = MutableStateFlow<String?>(null)
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
        override suspend fun navigate(path: String): NavigateResult = NavigateResult.Navigated
        override fun refresh() {}
        override fun getParentPath(path: String): String? = null
        override fun displayNameOf(path: String): String = path.substringAfterLast('/').ifEmpty { path }
    }
}
