package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.ui.model.AttributesInfo
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ItemKind
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.ui.model.SizeInfo
import com.todokanai.composepracticenew.usecase.FileInfoUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** [FileInfoViewModel]이 정보 다이얼로그를 읽는 중 상태로 열고 저장소에서 다시 읽은 결과를 반영하며, 닫거나 다시 열면 이전 결과를 버리는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class FileInfoViewModelTest {

    private val repo = GatedFileInfoRepository()
    private lateinit var viewModel: FileInfoViewModel

    private val folder = FileHolderItem(path = "/d/music", isDirectory = true, name = "music", size = "", lastModified = "")
    private val file = FileHolderItem(path = "/d/a.txt", isDirectory = false, name = "a.txt", size = "", lastModified = "", sizeBytes = 7)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = FileInfoViewModel(FileInfoUseCase(repo))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `폴더 하나를 열면 저장소 규칙의 위치와 읽는 중으로 시작해 속성, 크기, 하위 항목 수를 반영한다`() {
        viewModel.show(listOf(folder))
        val loading = viewModel.summary.value as SelectionSummary.Single
        assertEquals("위치:/d/music", loading.location)
        assertEquals(AttributesInfo.Loading, loading.attributes)
        assertEquals(SizeInfo.Loading, loading.size)
        assertNull(loading.childCount)

        repo.attributes.complete(IoResult.Success(ItemAttributes(isDirectory = true, lastModifiedMillis = 0)))
        assertEquals(ChildCountInfo.Loading, (viewModel.summary.value as SelectionSummary.Single).childCount)

        repo.size.complete(IoResult.Success(SizeMeasurement.Complete(2_048)))
        repo.childCount.complete(IoResult.Success(3))

        val loaded = viewModel.summary.value as SelectionSummary.Single
        assertEquals(ItemKind.Folder, (loaded.attributes as AttributesInfo.Known).kind)
        assertEquals(SizeInfo.Exact(readableFileSize_td(2_048)), loaded.size)
        assertEquals(ChildCountInfo.Known(3), loaded.childCount)
    }

    @Test
    fun `목록에 크기가 있는 파일도 저장소에서 다시 잴 때까지 읽는 중이다`() {
        viewModel.show(listOf(file))
        assertEquals(SizeInfo.Loading, viewModel.summary.value?.size)

        repo.size.complete(IoResult.Success(SizeMeasurement.Complete(9)))

        assertEquals(SizeInfo.Exact(readableFileSize_td(9)), viewModel.summary.value?.size)
    }

    @Test
    fun `목록에서 폴더였어도 지금 읽은 속성이 파일이면 하위 항목 수를 세지 않는다`() {
        viewModel.show(listOf(folder))

        repo.attributes.complete(IoResult.Success(ItemAttributes(isDirectory = false, lastModifiedMillis = 0)))

        val summary = viewModel.summary.value as SelectionSummary.Single
        assertEquals(ItemKind.File(null), (summary.attributes as AttributesInfo.Known).kind)
        assertNull(summary.childCount)
    }

    @Test
    fun `속성을 읽지 못하면 목록의 값 대신 실패를 보여주고 하위 항목 수를 세지 않는다`() {
        viewModel.show(listOf(folder))

        repo.attributes.complete(IoResult.ItemFailed("없음"))

        val summary = viewModel.summary.value as SelectionSummary.Single
        assertEquals(AttributesInfo.Failed("없음"), summary.attributes)
        assertNull(summary.childCount)
    }

    @Test
    fun `계산 중에 닫으면 늦게 온 결과로 다시 열리지 않는다`() {
        viewModel.show(listOf(folder))
        viewModel.dismiss()

        repo.attributes.complete(IoResult.Success(ItemAttributes(isDirectory = true, lastModifiedMillis = 0)))
        repo.size.complete(IoResult.Success(SizeMeasurement.Complete(1)))
        repo.childCount.complete(IoResult.Success(1))

        assertNull(viewModel.summary.value)
    }

    @Test
    fun `다른 선택으로 다시 열면 이전 선택의 속성 결과를 반영하지 않는다`() {
        viewModel.show(listOf(folder))
        viewModel.show(listOf(file, file.copy(path = "/d/b.txt", sizeBytes = 3)))

        repo.attributes.complete(IoResult.Success(ItemAttributes(isDirectory = true, lastModifiedMillis = 0)))

        assertEquals(SelectionSummary.Multiple(2, SizeInfo.Loading), viewModel.summary.value)
    }

    /** 테스트가 결과를 내줄 때까지 속성·측정·하위 항목 수 응답을 미루는 저장소. */
    private class GatedFileInfoRepository : FileInfoRepository {
        val attributes = CompletableDeferred<IoResult<ItemAttributes>>()
        val size = CompletableDeferred<IoResult<SizeMeasurement>>()
        val childCount = CompletableDeferred<IoResult<Int>>()

        override fun locationOf(path: String): String = "위치:$path"

        override suspend fun readAttributes(path: String): IoResult<ItemAttributes> = attributes.await()

        override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = size.await()

        override suspend fun countChildren(path: String): IoResult<Int> = childCount.await()
    }
}
