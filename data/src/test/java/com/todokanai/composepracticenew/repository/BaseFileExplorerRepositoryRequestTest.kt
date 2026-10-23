package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.SortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [BaseFileExplorerRepository]가 같은 경로로 다시 이동해도(재연결 등) 새 조회 요청을 만들어 목록을 다시 조회하고,
 * 공유가 멈추면 남아 있는 목록이 현재 요청의 목록으로 쓰이지 않도록 요청을 바꾸는지 검증하는 단위 테스트(#215).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseFileExplorerRepositoryRequestTest {

    private val sortPreference = object : SortPreferenceRepository {
        override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
        override fun saveSortBy(value: SortMode) { sortBy.value = value }
    }
    /** 다음 조회가 연결 끊김으로 실패해야 하면 true. */
    private var disconnected = false
    /** 조회 횟수. */
    private var fetchCount = 0
    /** 테스트가 만든 공유 스코프. 테스트가 끝나면 취소한다. */
    private val sharingScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = sharingScopes.forEach { it.cancel() }

    @Test
    fun `연결이 끊겨 실패한 경로로 다시 이동하면 다시 조회해 실패 상태가 풀린다`() = runTest {
        val repo = repositoryIn(this)
        disconnected = true
        repo.navigate("/root")
        runCurrent()
        assertEquals(ListingFailureReason.CONNECTION_LOST, (repo.directoryListing.value as DirectoryListing.Failed).reason)

        disconnected = false
        repo.navigate("/root")
        runCurrent()

        assertEquals(DirectoryListing.Loaded(repo.listingRequest.value!!, emptyList()), repo.directoryListing.value)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `새로고침은 같은 경로의 새 요청이다`() = runTest {
        val repo = repositoryIn(this)
        repo.navigate("/root")
        runCurrent()
        val before = repo.listingRequest.value!!

        repo.refresh()
        runCurrent()

        val after = repo.listingRequest.value!!
        assertEquals(before.path, after.path)
        assertNotEquals(before, after)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `공유가 멈추면 요청이 바뀌어 남아 있는 목록이 현재 요청의 목록이 아니게 된다`() = runTest {
        val repo = repositoryIn(this, SharingStarted.WhileSubscribed())
        val subscriber = launch { repo.directoryListing.collect {} }
        repo.navigate("/root")
        runCurrent()
        val loaded = repo.directoryListing.value as DirectoryListing.Loaded
        assertEquals(repo.listingRequest.value, loaded.request)

        subscriber.cancel()
        runCurrent()

        assertEquals("멈춘 동안 목록은 그대로 남는다", loaded, repo.directoryListing.value)
        assertNotEquals("남은 목록은 현재 요청의 목록으로 확인되지 않아야 함", repo.listingRequest.value, loaded.request)
        assertEquals("/root", repo.listingRequest.value?.path)
    }

    /** 테스트 스케줄러를 쓰는 공유 스코프에서 검증 없이 이동하는 테스트용 저장소를 만든다. */
    private fun repositoryIn(testScope: TestScope, started: SharingStarted = SharingStarted.Eagerly): BaseFileExplorerRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScope.testScheduler))
        sharingScopes += scope
        return object : BaseFileExplorerRepository(DataConverter(), sortPreference, scope, started) {
            override suspend fun listFiles(path: String): IoResult<FetchedListing> {
                fetchCount++
                if (disconnected) return IoResult.ConnectionLost("끊김")
                return IoResult.Success(FetchedListing.complete(emptyList()))
            }
            override suspend fun navigate(path: String) = navigateTo(path)
            override fun getParentPath(path: String): String? = null
        }
    }
}
