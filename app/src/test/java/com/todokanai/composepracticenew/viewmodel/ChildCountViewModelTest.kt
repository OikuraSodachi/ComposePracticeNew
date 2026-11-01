package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** [ChildCountViewModel]이 목록 조회 결과마다 폴더의 하위 항목 수를 한 번만 세어 다시 쓰고, 새 조회 결과는 다시 세며, 동시에 세는 수를 제한하는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChildCountViewModelTest {

    private val repo = GatedFileInfoRepository()
    private val viewModel = ChildCountViewModel(FileInfoUseCase(repo))

    private fun folder(name: String) =
        FileHolderItem(path = "/d/$name", isDirectory = true, name = name, size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)

    private val music = folder("music")
    private val listing = listOf(music)

    @Test
    fun `센 결과는 같은 목록에서 다시 세지 않고 지금 상태로도 돌려준다`() = runTest(UnconfinedTestDispatcher()) {
        val counts = viewModel.countsFor(listing)
        assertEquals(ChildCountInfo.Loading, counts.currentOf(music))

        val first = async { counts.countOf(music).first() }
        repo.complete(music.path, IoResult.Success(3))

        assertEquals(ChildCountInfo.Known(3), first.await())
        assertEquals(ChildCountInfo.Known(3), counts.countOf(music).first())
        assertEquals(ChildCountInfo.Known(3), counts.currentOf(music))
        assertEquals(1, repo.requestCount(music.path))
    }

    @Test
    fun `같은 목록 인스턴스에는 같은 결과를, 새 조회 결과에는 새로 센 결과를 돌려준다`() = runTest(UnconfinedTestDispatcher()) {
        val counts = viewModel.countsFor(listing)
        assertSame(counts, viewModel.countsFor(listing))
        val first = async { counts.countOf(music).first() }
        repo.complete(music.path, IoResult.Success(3))
        first.await()

        // 값이 같아도 새로 조회한 목록이다 — 폴더 내용이 바뀌었을 수 있다
        val refreshed = viewModel.countsFor(listOf(music.copy()))

        assertNotSame(counts, refreshed)
        assertEquals(ChildCountInfo.Loading, refreshed.currentOf(music))
        val second = async { refreshed.countOf(music).first() }
        repo.complete(music.path, IoResult.Success(5))
        assertEquals(ChildCountInfo.Known(5), second.await())
        assertEquals(2, repo.requestCount(music.path))
    }

    @Test
    fun `폴더를 읽지 못하면 0개가 아니라 실패를 돌려준다`() = runTest(UnconfinedTestDispatcher()) {
        val counts = viewModel.countsFor(listing)

        val result = async { counts.countOf(music).first() }
        repo.complete(music.path, IoResult.ItemFailed("권한 없음"))

        assertEquals(ChildCountInfo.Failed("권한 없음"), result.await())
    }

    @Test
    fun `세는 중에 구독을 취소하면 결과를 남기지 않아 다시 나타날 때 다시 센다`() = runTest(UnconfinedTestDispatcher()) {
        val counts = viewModel.countsFor(listing)

        launch { counts.countOf(music).first() }.cancel()

        assertEquals(ChildCountInfo.Loading, counts.currentOf(music))
        val again = async { counts.countOf(music).first() }
        repo.complete(music.path, IoResult.Success(1))
        assertEquals(ChildCountInfo.Known(1), again.await())
    }

    @Test
    fun `동시에 세는 폴더는 4개까지이고, 하나가 끝나면 기다리던 폴더를 센다`() = runTest(UnconfinedTestDispatcher()) {
        val folders = (1..5).map { folder("f$it") }
        val counts = viewModel.countsFor(folders)

        val results = folders.map { async { counts.countOf(it).first() } }

        assertEquals(4, repo.startedPaths().size)
        assertEquals(0, repo.requestCount(folders[4].path))

        repo.complete(folders[0].path, IoResult.Success(0))
        assertEquals(1, repo.requestCount(folders[4].path))

        folders.drop(1).forEach { repo.complete(it.path, IoResult.Success(2)) }
        assertEquals(listOf(ChildCountInfo.Known(0)) + List(4) { ChildCountInfo.Known(2) }, results.map { it.await() })
    }

    /** 테스트가 결과를 내줄 때까지 경로별 하위 항목 수 응답을 미루고, 경로별로 센 횟수를 기록하는 저장소. */
    private class GatedFileInfoRepository : FileInfoRepository {
        /** 경로별로 아직 응답하지 않은 세기. */
        private val pending = mutableMapOf<String, CompletableDeferred<IoResult<Int>>>()
        /** 경로별로 세기를 시작한 횟수. */
        private val requests = mutableMapOf<String, Int>()

        fun requestCount(path: String): Int = requests[path] ?: 0

        fun startedPaths(): Set<String> = pending.keys

        fun complete(path: String, result: IoResult<Int>) {
            pending.remove(path)!!.complete(result)
        }

        override fun locationOf(path: String): String? = null

        override suspend fun readAttributes(path: String): IoResult<ItemAttributes> = IoResult.ItemFailed(null)

        override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = IoResult.ItemFailed(null)

        override suspend fun countChildren(path: String): IoResult<Int> {
            requests[path] = requestCount(path) + 1
            return CompletableDeferred<IoResult<Int>>().also { pending[path] = it }.await()
        }
    }
}
