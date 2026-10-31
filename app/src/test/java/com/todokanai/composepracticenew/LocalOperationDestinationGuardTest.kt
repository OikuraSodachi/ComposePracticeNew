package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem

/**
 * 로컬 복사·이동·압축 해제·이름 변경이 현재 조회 요청의 확인된 목록으로만 이름 충돌을 판정하고,
 * 목록을 확인하지 못했으면 실행하지 않고 알리는지 검증하는 단위 테스트(#215).
 * 파일 작업 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 작업이 호출 즉시 끝난다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalOperationDestinationGuardTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val nav = FakeNavigator()
    private val progressRepo = RecordingProgressRepository()
    private val fileActionRepo = RecordingFileActionRepository()
    private lateinit var viewModel: FileListViewModel
    private lateinit var request: ListingRequest

    private val selected = listOf(
        FileHolderItem(path = "/src/a.txt", isDirectory = false, name = "a.txt", size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
    )
    private val renameTarget =
        FileHolderItem(path = "/dest/a.txt", isDirectory = false, name = "a.txt", size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, operationScope)
        viewModel = FileListViewModel(
            fileNavigatorUseCase = FileNavigatorUseCase(nav),
            openFileUseCase = OpenFileUseCase(nav) {},
            sortModeUseCase = SortModeUseCase(InMemorySortPreference()),
            localFileOperationUseCase = LocalFileOperationUseCase(orchestrator, fileActionRepo, FileNameUseCase()),
            conflictCheckUseCase = ConflictCheckUseCase(orchestrator) { FixedMessages.destinationUnverified },
            messages = FixedMessages
        )
        request = nav.moveTo("/dest")
    }

    @After
    fun tearDown() {
        operationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `현재 폴더 목록 조회에 실패했으면 복사하지 않고 알리며 선택을 유지한다`() {
        nav.directoryListing.value = DirectoryListing.Failed(request, ListingFailureReason.UNREADABLE, null)

        val executed = viewModel.requestConfirm(selected, SelectMode.LocalOperation.Copy)

        assertFalse("실행하지 않았으므로 호출부가 선택 모드를 초기화하지 않아야 함", executed)
        assertTrue(fileActionRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
    }

    @Test
    fun `현재 폴더 목록을 조회하는 중이면 이동하지 않는다`() {
        nav.directoryListing.value = DirectoryListing.Loading(request)

        assertFalse(viewModel.requestConfirm(selected, SelectMode.LocalOperation.Move))
        assertTrue(fileActionRepo.calls.isEmpty())
    }

    @Test
    fun `새로고침 중 직전 목록만 있으면 붙여넣기를 실행하지 않는다`() {
        val stale = DirectoryListing.Loaded(ListingRequest("/dest", 0L), emptyList())
        nav.directoryListing.value = DirectoryListing.Loading(request, stale)

        assertFalse(viewModel.requestConfirm(selected, SelectMode.LocalOperation.Copy))
        assertTrue(fileActionRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
    }

    @Test
    fun `확인된 목록에 같은 이름이 없으면 바로 복사한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, emptyList())

        assertTrue(viewModel.requestConfirm(selected, SelectMode.LocalOperation.Copy))
        assertEquals(listOf("copy"), fileActionRepo.calls)
        assertTrue(progressRepo.errors.isEmpty())
    }

    @Test
    fun `이름 변경은 이전 요청의 목록으로 판정하지 않고 실행하지 않는다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(ListingRequest("/dest", 0L), emptyList())

        viewModel.rename(renameTarget, "b.txt")

        assertTrue(fileActionRepo.calls.isEmpty())
        assertNull(viewModel.pendingRename.value)
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
    }

    @Test
    fun `이름 변경은 현재 요청의 확인된 목록으로 겹침을 판정한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, listOf(domainItem("/dest/a.txt"), domainItem("/dest/b.txt")))

        viewModel.rename(renameTarget, "b.txt")

        assertTrue("겹치므로 확인을 기다려야 함", fileActionRepo.calls.isEmpty())
        assertEquals("b.txt", viewModel.pendingRename.value?.newName)
    }

    @Test
    fun `이름 입력 중 검증은 목록을 확인하지 못했어도 오류를 알리지 않는다`() {
        nav.directoryListing.value = DirectoryListing.Loading(request)

        val errors = viewModel.validateRename(renameTarget, "b.txt")

        assertFalse(FileNameError.EXISTS_AS_OTHER_TYPE in errors)
        assertTrue(progressRepo.errors.isEmpty())
    }

    /** [path]의 domain 파일 항목을 만든다. */
    private fun domainItem(path: String) =
        DomainFileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)
}
