package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [FileListFlowProducer]가 요청마다 목록 조회 상태를 만들며, 조회 실패를 빈 목록 대신 실패 상태로,
 * 새 요청을 조회하는 동안을 이전 목록 대신 조회 중 상태로 나타내는지 검증하는 단위 테스트(#215).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileListFlowProducerTest {

    private val sortPreference = object : SortPreferenceRepository {
        override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
        override fun saveSortBy(value: SortMode) { sortBy.value = value }
    }
    private val producer = FileListFlowProducer(DataConverter(), sortPreference)
    private val requests = MutableStateFlow<ListingRequest?>(null)
    /** 경로 → 조회 동작. 등록되지 않은 경로는 빈 폴더로 본다. */
    private val responses = mutableMapOf<String, suspend () -> IoResult<List<FileEntry>>>()
    /** 경로별 조회 횟수. */
    private val fetchCounts = mutableMapOf<String, Int>()
    /** 공유가 멈춘 횟수. */
    private var stopCount = 0
    /** 기록된 예상하지 못한 예외. */
    private val unexpectedFailures = mutableListOf<Throwable>()
    /** 테스트가 만든 공유 스코프. 테스트가 끝나면 취소한다. */
    private val sharingScopes = mutableListOf<CoroutineScope>()
    private var generation = 0L

    @After
    fun tearDown() = sharingScopes.forEach { it.cancel() }

    @Test
    fun `요청이 없으면 경로 없음 상태다`() = runTest {
        val listing = listingIn(this)
        runCurrent()

        assertEquals(DirectoryListing.NoPath, listing.value)
    }

    @Test
    fun `빈 폴더는 항목이 없는 조회 완료 상태다`() = runTest {
        val listing = listingIn(this)
        val request = request("/a")
        runCurrent()

        assertEquals(DirectoryListing.Loaded(request, emptyList()), listing.value)
    }

    @Test
    fun `조회가 실패하면 빈 목록이 아니라 서버 응답을 담은 실패 상태다`() = runTest {
        responses["/a"] = { IoResult.ItemFailed("LIST 실패: 550 Permission denied") }
        val listing = listingIn(this)
        val request = request("/a")
        runCurrent()

        assertEquals(
            DirectoryListing.Failed(request, ListingFailureReason.UNREADABLE, "LIST 실패: 550 Permission denied"),
            listing.value
        )
    }

    @Test
    fun `연결이 없거나 끊겨 조회하지 못하면 연결 끊김 실패다`() = runTest {
        responses["/a"] = { IoResult.ConnectionLost("끊김") }
        val listing = listingIn(this)
        val request = request("/a")
        runCurrent()

        assertEquals(DirectoryListing.Failed(request, ListingFailureReason.CONNECTION_LOST, "끊김"), listing.value)
    }

    @Test
    fun `입출력 실패가 아닌 예외는 기록하고 내부 메시지 없이 실패 상태로 바꾸며 이후 요청도 계속 조회한다`() = runTest {
        val bug = IllegalStateException("파서 오류")
        responses["/a"] = { throw bug }
        val listing = listingIn(this)
        val failed = request("/a")
        runCurrent()

        assertEquals(DirectoryListing.Failed(failed, ListingFailureReason.UNEXPECTED, detail = null), listing.value)
        assertEquals(listOf<Throwable>(bug), unexpectedFailures)

        val next = request("/b")
        runCurrent()
        assertEquals(DirectoryListing.Loaded(next, emptyList()), listing.value)
    }

    @Test
    fun `다른 경로로 이동하면 조회가 끝날 때까지 이전 경로의 목록 없이 조회 중 상태다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        responses["/a"] = { ok(entry("/a/x.txt")) }
        responses["/b"] = { gate.await(); ok(entry("/b/y.txt")) }
        val listing = listingIn(this)
        request("/a")
        runCurrent()

        val toB = request("/b")
        runCurrent()
        assertEquals(DirectoryListing.Loading(toB), listing.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("/b/y.txt"), loadedPaths(listing.value))
    }

    @Test
    fun `같은 경로를 다시 조회하는 동안은 직전 목록을 표시용으로만 담은 조회 중 상태다`() = runTest {
        responses["/a"] = { ok(entry("/a/old.txt")) }
        val listing = listingIn(this)
        request("/a")
        runCurrent()
        val before = listing.value as DirectoryListing.Loaded

        val gate = CompletableDeferred<Unit>()
        responses["/a"] = { gate.await(); ok(entry("/a/new.txt")) }
        val refresh = request("/a")
        runCurrent()
        assertEquals(DirectoryListing.Loading(refresh, stale = before), listing.value)
        assertSame("직전 목록을 다시 정렬하지 않고 이미 방출한 인스턴스를 재사용해야 함", before, (listing.value as DirectoryListing.Loading).stale)

        gate.complete(Unit)
        runCurrent()
        assertEquals(DirectoryListing.Loaded(refresh, DataConverter().fileHolderItemList(listOf(entry("/a/new.txt")), SortMode.BY_DEFAULT)), listing.value)
    }

    @Test
    fun `같은 경로를 다시 조회하다 실패하면 직전 목록 대신 실패 상태가 된다`() = runTest {
        responses["/a"] = { ok(entry("/a/x.txt")) }
        val listing = listingIn(this)
        request("/a")
        runCurrent()

        responses["/a"] = { IoResult.ItemFailed("LIST 실패: 550 Permission denied") }
        val refresh = request("/a")
        runCurrent()

        assertEquals(
            DirectoryListing.Failed(refresh, ListingFailureReason.UNREADABLE, "LIST 실패: 550 Permission denied"),
            listing.value
        )
    }

    @Test
    fun `다른 경로를 거쳐 같은 경로로 돌아오면 그 경로의 옛 목록을 직전 목록으로 쓰지 않는다`() = runTest {
        val gateB = CompletableDeferred<Unit>()
        val gateA = CompletableDeferred<Unit>()
        responses["/a"] = { ok(entry("/a/x.txt")) }
        responses["/b"] = { gateB.await(); ok() }
        val listing = listingIn(this)
        request("/a")
        runCurrent()

        request("/b")
        runCurrent()
        responses["/a"] = { gateA.await(); ok(entry("/a/x.txt")) }
        val back = request("/a")
        runCurrent()

        assertEquals(DirectoryListing.Loading(back), listing.value)
    }

    @Test
    fun `정렬 기준만 바뀌면 다시 조회하지 않고 조회한 항목을 다시 정렬한다`() = runTest {
        responses["/a"] = { ok(entry("/a/b.txt"), entry("/a/a.txt")) }
        val listing = listingIn(this)
        request("/a")
        runCurrent()
        assertEquals(listOf("/a/a.txt", "/a/b.txt"), loadedPaths(listing.value))

        sortPreference.sortBy.value = SortMode.BY_NAME_DESCENDING
        runCurrent()

        assertEquals(listOf("/a/b.txt", "/a/a.txt"), loadedPaths(listing.value))
        assertEquals(1, fetchCounts["/a"])
    }

    @Test
    fun `공유가 멈추면 알리고, 다시 시작해 같은 경로를 조회하는 동안은 멈추기 전 목록을 표시용으로 담는다`() = runTest {
        responses["/a"] = { ok(entry("/a/x.txt")) }
        val listing = listingIn(this, SharingStarted.WhileSubscribed())
        val first = launch { listing.collect {} }
        request("/a")
        runCurrent()
        val before = listing.value as DirectoryListing.Loaded

        first.cancel()
        runCurrent()
        assertEquals(1, stopCount)

        val gate = CompletableDeferred<Unit>()
        responses["/a"] = { gate.await(); ok(entry("/a/x.txt")) }
        val afterRestart = request("/a")
        val second = launch { listing.collect {} }
        runCurrent()
        assertEquals(DirectoryListing.Loading(afterRestart, stale = before), listing.value)

        gate.complete(Unit)
        second.cancel()
    }

    /** 테스트 스케줄러를 쓰는 공유 스코프에서 목록 상태 StateFlow를 만든다. */
    private fun listingIn(testScope: TestScope, started: SharingStarted = SharingStarted.Eagerly): StateFlow<DirectoryListing> {
        val scope = CoroutineScope(StandardTestDispatcher(testScope.testScheduler))
        sharingScopes += scope
        return producer.produce(
            requests = requests,
            appScope = scope,
            started = started,
            onStopped = { stopCount++ },
            onUnexpectedFailure = { unexpectedFailures += it }
        ) { path ->
            fetchCounts[path] = (fetchCounts[path] ?: 0) + 1
            responses[path]?.invoke() ?: ok()
        }
    }

    /** [path]의 새 조회 요청을 보내고 그 요청을 반환한다. */
    private fun request(path: String): ListingRequest = ListingRequest(path, ++generation).also { requests.value = it }

    /** 조회 완료 상태의 항목 경로를 순서대로 반환한다. */
    private fun loadedPaths(listing: DirectoryListing): List<String> = (listing as DirectoryListing.Loaded).items.map { it.path }

    /** [path]의 1바이트 파일 항목을 만든다. */
    private fun entry(path: String) =
        FileEntry(name = path.substringAfterLast('/'), path = path, isDirectory = false, size = 1L, lastModified = 0L)

    /** [entries]를 담은 조회 성공 결과를 만든다. */
    private fun ok(vararg entries: FileEntry): IoResult<List<FileEntry>> = IoResult.Success(entries.toList())
}
