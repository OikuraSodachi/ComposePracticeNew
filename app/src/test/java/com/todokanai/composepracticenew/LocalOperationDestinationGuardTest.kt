package com.todokanai.composepracticenew

import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ExtractLayout
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
import com.todokanai.composepracticenew.viewmodel.LocalOperationConfirmer
import kotlinx.coroutines.CompletableDeferred
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
 * 확인한 작업은 확정이므로 판정이 화면 수명과 무관하게 끝까지 진행되는지도 검증한다(#315 리뷰).
 * 파일 작업 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 작업이 호출 즉시 끝난다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalOperationDestinationGuardTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val nav = FakeNavigator()
    private val progressRepo = RecordingProgressRepository()

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
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
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, notices, GrantedProtection, operationScope)
        val navigator = FileNavigatorUseCase(nav, nav)
        val localFileOperationUseCase = LocalFileOperationUseCase(orchestrator, notices, fileActionRepo, FileNameUseCase(), nav)
        val conflictCheckUseCase = ConflictCheckUseCase(notices) { FixedMessages.destinationUnverified }
        viewModel = FileListViewModel(
            fileNavigatorUseCase = navigator,
            openFileUseCase = OpenFileUseCase(nav) {},
            sortModeUseCase = SortModeUseCase(InMemorySortPreference()),
            localFileOperationUseCase = localFileOperationUseCase,
            conflictCheckUseCase = conflictCheckUseCase,
            messages = FixedMessages,
            localOperationConfirmer = LocalOperationConfirmer(navigator, localFileOperationUseCase, conflictCheckUseCase, notices, FixedMessages, operationScope)
        )
        request = nav.moveTo("/dest")
    }

    @After
    fun tearDown() {
        operationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `현재 폴더 목록 조회에 실패했으면 복사하지 않고 알린다`() {
        nav.directoryListing.value = DirectoryListing.Failed(request, ListingFailureReason.UNREADABLE, null)

        confirm(SelectMode.LocalOperation.Copy)

        assertTrue(fileActionRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), notices.failures)
    }

    @Test
    fun `현재 폴더 목록을 조회하는 중이면 이동하지 않는다`() {
        nav.directoryListing.value = DirectoryListing.Loading(request)

        confirm(SelectMode.LocalOperation.Move)

        assertTrue(fileActionRepo.calls.isEmpty())
    }

    @Test
    fun `새로고침 중 직전 목록만 있으면 붙여넣기를 실행하지 않는다`() {
        val stale = DirectoryListing.Loaded(ListingRequest("/dest", 0L), emptyList())
        nav.directoryListing.value = DirectoryListing.Loading(request, stale)

        confirm(SelectMode.LocalOperation.Copy)

        assertTrue(fileActionRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), notices.failures)
    }

    @Test
    fun `확인된 목록에 같은 이름이 없으면 바로 복사한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, emptyList())

        confirm(SelectMode.LocalOperation.Copy)

        assertEquals(listOf("copy"), fileActionRepo.calls)
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `확인된 목록에 같은 이름이 없으면 바로 이동한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, emptyList())

        confirm(SelectMode.LocalOperation.Move)

        assertEquals(listOf("move"), fileActionRepo.calls)
    }

    @Test
    fun `압축 해제는 작업 종류에 따라 배치 방식을 구분해 실행한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, emptyList())

        confirm(SelectMode.LocalOperation.Unzip)
        confirm(SelectMode.LocalOperation.UnzipHere)

        assertEquals(listOf("unzip", "unzip"), fileActionRepo.calls)
        assertEquals(listOf(ExtractLayout.IntoArchiveFolder, ExtractLayout.IntoDestination), fileActionRepo.unzipLayouts)
    }

    @Test
    fun `여기에 압축 해제는 풀 항목과 같은 이름이 목적지에 있으면 실행하지 않고 확인을 요청한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, listOf(domainItem("/dest/readme.txt")))
        fileActionRepo.extractedNamesOf = { _, layout -> if (layout == ExtractLayout.IntoDestination) setOf("readme.txt") else setOf("a") }

        confirm(SelectMode.LocalOperation.UnzipHere)

        assertTrue(fileActionRepo.calls.isEmpty())
        assertEquals(selected, viewModel.pendingConflict.value?.conflicts)
    }

    @Test
    fun `여기에 압축 해제는 아카이브 이름의 폴더가 목적지에 있어도 풀 항목과 겹치지 않으면 바로 실행한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, listOf(domainItem("/dest/a")))
        fileActionRepo.extractedNamesOf = { _, layout -> if (layout == ExtractLayout.IntoDestination) setOf("readme.txt") else setOf("a") }

        confirm(SelectMode.LocalOperation.UnzipHere)

        assertEquals(listOf(ExtractLayout.IntoDestination), fileActionRepo.unzipLayouts)
    }

    @Test
    fun `확인한 압축 해제는 판정 중에 화면이 정리돼도 판정이 끝나면 실행한다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, emptyList())
        val archiveRead = CompletableDeferred<Unit>()
        fileActionRepo.extractedNamesGate = archiveRead

        confirm(SelectMode.LocalOperation.UnzipHere)
        assertTrue("아카이브를 읽는 중이므로 아직 실행하지 않아야 함", fileActionRepo.calls.isEmpty())

        viewModel.viewModelScope.cancel()
        archiveRead.complete(Unit)

        assertEquals(listOf("unzip"), fileActionRepo.calls)
    }

    @Test
    fun `다른 작업이 충돌 확인을 기다리는 동안 확정한 작업은 실행하지 않고 알리며 기다리는 작업을 바꾸지 않는다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(request, listOf(domainItem("/dest/a.txt")))
        confirm(SelectMode.LocalOperation.Copy)
        val pending = viewModel.pendingConflict.value

        confirm(SelectMode.LocalOperation.Move)

        assertTrue(fileActionRepo.calls.isEmpty())
        assertEquals(pending, viewModel.pendingConflict.value)
        assertEquals(listOf(FixedMessages.conflictAwaiting), notices.failures)
    }

    @Test
    fun `이름 변경은 이전 요청의 목록으로 판정하지 않고 실행하지 않는다`() {
        nav.directoryListing.value = DirectoryListing.Loaded(ListingRequest("/dest", 0L), emptyList())

        viewModel.rename(renameTarget, "b.txt")

        assertTrue(fileActionRepo.calls.isEmpty())
        assertNull(viewModel.pendingRename.value)
        assertEquals(listOf(FixedMessages.destinationUnverified), notices.failures)
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
        assertTrue(notices.failures.isEmpty())
    }

    /** [selected]로 [mode] 작업을 확정한다. 판정이 일시 중단하지 않으면 호출 안에서 끝난다. */
    private fun confirm(mode: SelectMode.LocalOperation) = viewModel.requestConfirm(selected, mode)

    /** [path]의 domain 파일 항목을 만든다. */
    private fun domainItem(path: String) =
        DomainFileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)
}
