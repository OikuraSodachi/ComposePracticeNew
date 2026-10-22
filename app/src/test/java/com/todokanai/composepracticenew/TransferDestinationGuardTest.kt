package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.ui.model.FileHolderItem
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem

/**
 * 다운로드·업로드 확인이 대상 폴더의 확인된 목록으로만 이름 충돌을 판정하고,
 * 목록을 확인하지 못했으면(조회 중·실패·이전 경로) 확인 없이 덮어쓰지 않도록 실행하지 않는지 검증하는 단위 테스트(#215).
 * 전송 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 작업이 호출 즉시 끝난다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferDestinationGuardTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val localNav = FakeNavigator()
    private val remoteNav = FakeNavigator()
    private val progressRepo = RecordingProgressRepository()
    private val transferRepo = RecordingTransferRepository()
    private lateinit var viewModel: FileTransferCoordinatorViewModel
    private lateinit var localRequest: ListingRequest
    private lateinit var remoteRequest: ListingRequest

    private val upload = appItem("/local/a.txt")
    private val download = appItem("ftp://h/r/b.txt")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, operationScope)
        val remoteOps = RemoteFileOperationUseCase(
            orchestrator,
            transferRepo,
            NoOpMutationRepository,
            FileNameUseCase()
        )
        viewModel = FileTransferCoordinatorViewModel(
            FileNavigatorUseCase(localNav),
            FileNavigatorUseCase(remoteNav),
            remoteOps,
            ConflictCheckUseCase(orchestrator) { FixedMessages.destinationUnverified },
            FixedMessages
        )
        localRequest = localNav.moveTo("/local")
        remoteRequest = remoteNav.moveTo("ftp://h/r")
    }

    @After
    fun tearDown() {
        operationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `업로드 대상 목록 조회에 실패했으면 업로드하지 않고 알리며 대기 목록을 유지한다`() {
        remoteNav.directoryListing.value = DirectoryListing.Failed(remoteRequest, ListingFailureReason.UNREADABLE, "550")
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
        assertEquals(listOf(upload), viewModel.uploadPendingList.value)
    }

    @Test
    fun `업로드 대상 목록을 조회하는 중이면 업로드하지 않는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loading(remoteRequest)
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
    }

    @Test
    fun `목록이 아직 이전 경로의 것이면 업로드하지 않는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(ListingRequest("ftp://h/old", 0L), emptyList())
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
    }

    @Test
    fun `확인된 대상 목록에 같은 이름이 있으면 덮어쓰기 확인을 기다린다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, listOf(domainItem("ftp://h/r/a.txt")))
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertNotNull(viewModel.uploadConflict.value)
    }

    @Test
    fun `확인된 대상 목록에 같은 이름이 없으면 업로드한다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        enterUpload()

        viewModel.confirmUpload()

        assertEquals(listOf("upload /local/a.txt"), transferRepo.calls)
        assertTrue(progressRepo.errors.isEmpty())
    }

    @Test
    fun `다운로드 대상 로컬 목록 조회에 실패했으면 다운로드하지 않고 대기 목록을 유지한다`() {
        localNav.directoryListing.value = DirectoryListing.Failed(localRequest, ListingFailureReason.UNREADABLE, null)
        viewModel.enterDownloadMode(listOf(download))

        viewModel.confirmDownload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), progressRepo.errors)
        assertEquals(listOf(download), viewModel.downloadPendingList.value)
    }

    /** 로컬 선택 목록을 업로드 대기 목록으로 옮긴다. */
    private fun enterUpload() {
        viewModel.setLocalSelectedList(listOf(upload))
        viewModel.enterUploadMode()
    }

    /** [path]의 앱 레이어 파일 항목을 만든다. */
    private fun appItem(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), size = "", lastModified = "")

    /** [path]의 domain 파일 항목을 만든다. */
    private fun domainItem(path: String) =
        DomainFileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)
}
