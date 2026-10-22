package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * [FileListFlowProducer]가 조회 실패를 빈 목록 대신 실패 상태로, 다른 경로로 이동한 직후를 이전 목록 대신 조회 중 상태로 나타내는지 검증하는 단위 테스트(#215).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileListFlowProducerTest {

    private val sortPreference = object : SortPreferenceRepository {
        override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
        override fun saveSortBy(value: SortMode) { sortBy.value = value }
    }
    private val producer = FileListFlowProducer(DataConverter(), sortPreference)
    private val currentPath = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0L)
    /** 경로 → 조회 동작. 등록되지 않은 경로는 빈 폴더로 본다. */
    private val responses = mutableMapOf<String, suspend () -> List<FileEntry>>()
    /** 테스트가 만든 공유 스코프. 테스트가 끝나면 취소한다. */
    private val sharingScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = sharingScopes.forEach { it.cancel() }

    @Test
    fun `경로가 없으면 경로 없음 상태다`() = runTest {
        val listing = listingIn(this)
        runCurrent()

        assertEquals(DirectoryListing.NoPath, listing.value)
    }

    @Test
    fun `빈 폴더는 항목이 없는 조회 완료 상태다`() = runTest {
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()

        assertEquals(DirectoryListing.Loaded("/a", emptyList()), listing.value)
    }

    @Test
    fun `조회가 실패하면 빈 목록이 아니라 서버 응답을 담은 실패 상태다`() = runTest {
        responses["/a"] = { throw IOException("LIST 실패: 550 Permission denied") }
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()

        assertEquals(
            DirectoryListing.Failed("/a", ListingFailureReason.UNREADABLE, "LIST 실패: 550 Permission denied"),
            listing.value
        )
    }

    @Test
    fun `연결이 없거나 끊겨 조회하지 못하면 연결 끊김 실패다`() = runTest {
        responses["/a"] = { throw RemoteConnectionLostException("끊김") }
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()

        assertEquals(DirectoryListing.Failed("/a", ListingFailureReason.CONNECTION_LOST, "끊김"), listing.value)
    }

    @Test
    fun `다른 경로로 이동하면 조회가 끝날 때까지 이전 경로의 목록 대신 조회 중 상태다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        responses["/a"] = { listOf(entry("/a/x.txt")) }
        responses["/b"] = { gate.await(); listOf(entry("/b/y.txt")) }
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()

        currentPath.value = "/b"
        runCurrent()
        assertEquals(DirectoryListing.Loading("/b"), listing.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("/b/y.txt"), (listing.value as DirectoryListing.Loaded).items.map { it.path })
    }

    @Test
    fun `실패 후 다시 시도하면 조회 중을 거쳐 결과가 된다`() = runTest {
        responses["/a"] = { throw IOException("LIST 실패: 450 Busy") }
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()

        val gate = CompletableDeferred<Unit>()
        responses["/a"] = { gate.await(); listOf(entry("/a/x.txt")) }
        refreshTrigger.value = 1L
        runCurrent()
        assertEquals(DirectoryListing.Loading("/a"), listing.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals("/a", (listing.value as DirectoryListing.Loaded).path)
    }

    @Test
    fun `같은 경로를 새로고침하는 동안에는 그 경로의 기존 목록을 유지한다`() = runTest {
        responses["/a"] = { listOf(entry("/a/old.txt")) }
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()
        val before = listing.value

        val gate = CompletableDeferred<Unit>()
        responses["/a"] = { gate.await(); listOf(entry("/a/new.txt")) }
        refreshTrigger.value = 1L
        runCurrent()
        assertEquals(before, listing.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("/a/new.txt"), (listing.value as DirectoryListing.Loaded).items.map { it.path })
    }

    @Test
    fun `같은 경로를 다시 조회하다 실패하면 기존 목록 대신 실패 상태가 된다`() = runTest {
        responses["/a"] = { listOf(entry("/a/x.txt")) }
        val listing = listingIn(this)
        currentPath.value = "/a"
        runCurrent()

        responses["/a"] = { throw IOException("LIST 실패: 550 Permission denied") }
        refreshTrigger.value = 1L
        runCurrent()

        assertEquals(
            DirectoryListing.Failed("/a", ListingFailureReason.UNREADABLE, "LIST 실패: 550 Permission denied"),
            listing.value
        )
    }

    /** 테스트 스케줄러를 쓰는 공유 스코프에서 목록 상태 StateFlow를 만든다. */
    private fun listingIn(testScope: TestScope): StateFlow<DirectoryListing> {
        val scope = CoroutineScope(StandardTestDispatcher(testScope.testScheduler))
        sharingScopes += scope
        return producer.produce(currentPath, refreshTrigger, scope, SharingStarted.Eagerly) { path ->
            responses[path]?.invoke() ?: emptyList()
        }
    }

    /** [path]의 1바이트 파일 항목을 만든다. */
    private fun entry(path: String) =
        FileEntry(name = path.substringAfterLast('/'), path = path, isDirectory = false, size = 1L, lastModified = 0L)
}
