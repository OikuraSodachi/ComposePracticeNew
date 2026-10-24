package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.viewmodel.FileTransferCoordinatorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 다운로드·업로드로 화면 사이를 오갈 때 목적지 화면에 이전 선택이 남지 않고, 넘겨준 화면은 기본 모드로 돌아가는지 검증하는 단위 테스트(#252 리뷰).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferSelectionTransitionTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private lateinit var viewModel: FileTransferCoordinatorViewModel

    private val localA = item("/local/a.txt")
    private val localB = item("/local/b.txt")
    private val remoteX = item("ftp://h/r/x.txt")
    private val remoteY = item("ftp://h/r/y.txt")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, RecordingProgressRepository(), operationScope)
        viewModel = FileTransferCoordinatorViewModel(
            FileNavigatorUseCase(FakeNavigator()),
            FileNavigatorUseCase(FakeNavigator()),
            RemoteFileOperationUseCase(orchestrator, RecordingTransferRepository(), NoOpMutationRepository, FileNameUseCase()),
            ConflictCheckUseCase(orchestrator) { FixedMessages.destinationUnverified },
            FixedMessages
        )
    }

    @After
    fun tearDown() {
        operationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `로컬에 선택이 남아 있어도 다운로드 목적지 화면에는 대기 목록만 있다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.addToLocalList(localB)
        viewModel.enterLocalOperation(SelectMode.LocalOperation.Copy)
        viewModel.startRemoteMultiSelect(remoteX)

        viewModel.enterDownloadMode(listOf(remoteX))

        assertEquals(SelectionState.Receiving(SelectMode.Download, listOf(remoteX)), viewModel.localSelection.value)
        assertEquals(emptyList<FileHolderItem>(), viewModel.localSelection.value.selected)
        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
    }

    @Test
    fun `원격에 선택이 남아 있어도 업로드 목적지 화면에는 대기 목록만 있다`() {
        viewModel.startRemoteMultiSelect(remoteX)
        viewModel.addToRemoteList(remoteY)
        viewModel.startLocalMultiSelect(localA)

        viewModel.enterUploadMode()

        assertEquals(SelectionState.Receiving(SelectMode.Upload, listOf(localA)), viewModel.remoteSelection.value)
        assertEquals(emptyList<FileHolderItem>(), viewModel.remoteSelection.value.selected)
        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `다운로드 목적지 확인을 취소하면 대기 목록도 사라진다`() {
        viewModel.enterDownloadMode(listOf(remoteX))

        viewModel.resetLocalSelection()

        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `업로드 목적지 확인을 취소하면 대기 목록도 사라진다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.enterUploadMode()

        viewModel.resetRemoteSelection()

        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
    }

    /** [path]의 앱 레이어 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), size = "", lastModified = "")
}
