package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteSessionCheckUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem

/** 다중 선택이 같은 폴더를 다시 조회한 목록에 맞춰지는지(#524) 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectionFollowsListingTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 로컬 목록 조회 상태를 테스트가 정하는 탐색 저장소. */
    private val localNav = FakeNavigator()
    /** 원격 목록 조회 상태를 테스트가 정하는 탐색 저장소. */
    private val remoteNav = FakeNavigator()
    private lateinit var viewModel: FileTransferCoordinatorViewModel

    private val request = ListingRequest("/local", 1L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        operationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `다시 조회한 목록에 없는 항목은 선택에서 빠지고 남은 항목은 새 값을 갖는다`() {
        viewModel.startLocalMultiSelect(item("/local/a.txt"))
        viewModel.addToLocalList(item("/local/b.txt"))

        localNav.directoryListing.value = loaded(domainItem("/local/b.txt", sizeBytes = 7L), domainItem("/local/c.txt"))

        val state = viewModel.localSelection.value
        assertTrue(state is SelectionState.MultiSelect)
        assertEquals(listOf("/local/b.txt"), state.selected.map { it.path })
        assertEquals(7L, state.selected.single().sizeBytes)
    }

    @Test
    fun `선택한 항목이 모두 사라져도 다중 선택은 유지한다`() {
        viewModel.startLocalMultiSelect(item("/local/a.txt"))

        localNav.directoryListing.value = loaded(domainItem("/local/c.txt"))

        assertEquals(SelectionState.MultiSelect(emptyList()), viewModel.localSelection.value)
    }

    @Test
    fun `다시 조회하는 중에는 선택을 그대로 둔다`() {
        val a = item("/local/a.txt")
        viewModel.startLocalMultiSelect(a)

        localNav.directoryListing.value = DirectoryListing.Loading(request.copy(generation = 2L), stale = loaded())

        assertEquals(SelectionState.MultiSelect(listOf(a)), viewModel.localSelection.value)
    }

    @Test
    fun `다시 조회에 실패하면 다중 선택을 끝낸다`() {
        viewModel.startLocalMultiSelect(item("/local/a.txt"))

        localNav.directoryListing.value = DirectoryListing.Failed(request, ListingFailureReason.entries.first(), failure = null)

        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `작업 목적지 확인 중에는 목록이 바뀌어도 선택을 그대로 둔다`() {
        val a = item("/local/a.txt")
        viewModel.startLocalMultiSelect(a)
        viewModel.enterLocalOperation(SelectMode.LocalOperation.Copy)

        localNav.directoryListing.value = loaded(domainItem("/local/other/x.txt"))
        localNav.directoryListing.value = DirectoryListing.Failed(request, ListingFailureReason.entries.first(), failure = null)

        assertEquals(SelectionState.Operation(SelectMode.LocalOperation.Copy, listOf(a)), viewModel.localSelection.value)
    }

    @Test
    fun `원격 다중 선택도 원격 목록에 맞춘다`() {
        viewModel.startRemoteMultiSelect(item("ftp://h/r/x.txt"))
        viewModel.startLocalMultiSelect(item("/local/a.txt"))

        remoteNav.directoryListing.value = loaded(domainItem("ftp://h/r/y.txt"))

        assertEquals(SelectionState.MultiSelect(emptyList()), viewModel.remoteSelection.value)
        assertEquals(listOf("/local/a.txt"), viewModel.localSelection.value.selected.map { it.path })
    }

    /** 이 테스트의 가짜 저장소로 검증 대상을 만든다. */
    private fun createViewModel(): FileTransferCoordinatorViewModel {
        val notices = RecordingOperationNotices()
        val connection = FakeConnectionRepository()
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, RecordingProgressReporter(), notices, GrantedProtection, UnexpectedQuestions, NoOpFailureRecorder, operationScope)
        return FileTransferCoordinatorViewModel(
            FileNavigatorUseCase(localNav, localNav),
            FileNavigatorUseCase(remoteNav, remoteNav),
            RemoteFileOperationUseCase(orchestrator, notices, RecordingTransferRepository(), NoOpMutationRepository, ConnectedSessionProvider, FileNameUseCase(NoStorageNameRules)),
            UploadConflictCheckUseCase(ConflictCheckUseCase(notices, ExactNameMatching), FakeFolderEntryNamesRepository(), ExactNameMatching),
            RemoteConnectionStateUseCase(connection),
            RemoteSessionCheckUseCase(connection, notices)
        )
    }

    /** [items]를 담은 조회 완료 상태를 만든다. */
    private fun loaded(vararg items: DomainFileHolderItem) = DirectoryListing.Loaded(request, items.toList(), unreadableCount = 0)

    /** [path]의 domain 파일 항목을 만든다. */
    private fun domainItem(path: String, sizeBytes: Long? = null) =
        DomainFileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = sizeBytes, lastModifiedMillis = 0L)

    /** [path]의 앱 레이어 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
