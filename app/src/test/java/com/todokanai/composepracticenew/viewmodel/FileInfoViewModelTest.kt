package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
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

/** [FileInfoViewModel]이 정보 다이얼로그를 계산 중 상태로 열고 결과를 반영하며, 닫거나 다시 열면 이전 계산 결과를 버리는지 검증하는 단위 테스트. */
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
    fun `폴더 하나를 열면 계산 중으로 시작해 크기와 하위 항목 수를 반영한다`() {
        viewModel.show(listOf(folder))
        val loading = viewModel.summary.value as SelectionSummary.Single
        assertEquals(SizeInfo.Loading, loading.size)
        assertEquals(ChildCountInfo.Loading, loading.childCount)

        repo.size.complete(IoResult.Success(SizeMeasurement.Complete(2_048)))
        repo.childCount.complete(IoResult.Success(3))

        val loaded = viewModel.summary.value as SelectionSummary.Single
        assertEquals(SizeInfo.Exact(readableFileSize_td(2_048)), loaded.size)
        assertEquals(ChildCountInfo.Known(3), loaded.childCount)
    }

    @Test
    fun `크기를 아는 파일 하나는 저장소를 기다리지 않고 바로 크기를 보여준다`() {
        viewModel.show(listOf(file))

        assertEquals(SizeInfo.Exact(readableFileSize_td(7)), viewModel.summary.value?.size)
    }

    @Test
    fun `계산 중에 닫으면 늦게 온 결과로 다시 열리지 않는다`() {
        viewModel.show(listOf(folder))
        viewModel.dismiss()

        repo.size.complete(IoResult.Success(SizeMeasurement.Complete(1)))
        repo.childCount.complete(IoResult.Success(1))

        assertNull(viewModel.summary.value)
    }

    @Test
    fun `다른 선택으로 다시 열면 이전 계산 결과를 반영하지 않는다`() {
        viewModel.show(listOf(folder))
        viewModel.show(listOf(file, file.copy(path = "/d/b.txt", sizeBytes = 3)))

        repo.size.complete(IoResult.Success(SizeMeasurement.Complete(999)))

        assertEquals(SelectionSummary.Multiple(2, SizeInfo.Exact(readableFileSize_td(10))), viewModel.summary.value)
    }

    /** 테스트가 결과를 내줄 때까지 측정·하위 항목 수 응답을 미루는 저장소. */
    private class GatedFileInfoRepository : FileInfoRepository {
        val size = CompletableDeferred<IoResult<SizeMeasurement>>()
        val childCount = CompletableDeferred<IoResult<Int>>()

        override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = size.await()

        override suspend fun countChildren(path: String): IoResult<Int> = childCount.await()
    }
}
