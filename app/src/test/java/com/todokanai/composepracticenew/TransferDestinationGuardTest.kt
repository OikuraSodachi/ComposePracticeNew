package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FolderEntryNames
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.NameCollisionGroup
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
import com.todokanai.composepracticenew.viewmodel.FileTransferCoordinatorViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()

    /** 업로드 전 겹침 확인이 읽을 폴더 안 항목 이름. */
    private val folderEntries = FolderEntriesInfoRepository()

    /** 원격 세션 끊김을 테스트가 일으키는 연결 저장소. 연결된 상태로 시작한다. */
    private val connection = FakeConnectionRepository()
    private val transferRepo = RecordingTransferRepository()
    private lateinit var viewModel: FileTransferCoordinatorViewModel
    private lateinit var localRequest: ListingRequest
    private lateinit var remoteRequest: ListingRequest

    private val upload = appItem("/local/a.txt")
    private val download = appItem("ftp://h/r/b.txt")

    /** 앞의 업로드를 확인하는 동안 들어오는 다음 업로드 항목. */
    private val other = appItem("/local/c.txt")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, notices, GrantedProtection, UnexpectedConflictQuestions, operationScope)
        val remoteOps = RemoteFileOperationUseCase(
            orchestrator,
            notices,
            transferRepo,
            NoOpMutationRepository,
            FileNameUseCase(NoStorageNameRules)
        )
        viewModel = FileTransferCoordinatorViewModel(
            FileNavigatorUseCase(localNav, localNav),
            FileNavigatorUseCase(remoteNav, remoteNav),
            remoteOps,
            UploadConflictCheckUseCase(ConflictCheckUseCase(notices, ExactNameMatching) { FixedMessages.destinationUnverified }, folderEntries, ExactNameMatching),
            FixedMessages,
            RemoteConnectionStateUseCase(connection)
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
        assertEquals(listOf(FixedMessages.destinationUnverified), notices.failures)
        assertEquals(SelectionState.Receiving(SelectMode.Upload, listOf(upload)), viewModel.remoteSelection.value)
    }

    @Test
    fun `업로드 대상 목록을 조회하는 중이면 업로드하지 않는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loading(remoteRequest)
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), notices.failures)
    }

    @Test
    fun `목록이 아직 이전 경로의 것이면 업로드하지 않는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(ListingRequest("ftp://h/old", 0L), emptyList())
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(listOf(FixedMessages.destinationUnverified), notices.failures)
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
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `함께 올리는 폴더 안 항목끼리 목적지에서 같은 이름이 될 수 있으면 올리지 않고 확인을 기다린다(#323)`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        folderEntries.entries = listOf(FolderEntryNames("photos", listOf("x.jpg", "x.jpg")))
        enterUpload()

        viewModel.confirmUpload()

        assertTrue(transferRepo.calls.isEmpty())
        assertEquals(PendingConflict(listOf(upload), emptyList(), listOf(NameCollisionGroup("photos", listOf("x.jpg", "x.jpg")))), viewModel.uploadConflict.value)
    }

    @Test
    fun `함께 올리는 항목끼리의 겹침을 확인하고 계속하면 모두 올린다(#323)`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        folderEntries.entries = listOf(FolderEntryNames("photos", listOf("x.jpg", "x.jpg")))
        enterUpload()
        viewModel.confirmUpload()

        viewModel.resolveUploadConflict(ConflictResolution.OVERWRITE)

        assertEquals(listOf("upload /local/a.txt"), transferRepo.calls)
    }

    @Test
    fun `확인하는 동안 다음 업로드가 들어와도 앞의 업로드는 사라지지 않고 둘 다 올라간다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()
        viewModel.confirmUpload()

        enterUpload(other)
        viewModel.confirmUpload()
        hold.complete(Unit)

        assertEquals(setOf("upload /local/a.txt", "upload /local/c.txt"), transferRepo.calls.toSet())
        assertEquals(2, transferRepo.calls.size)
    }

    @Test
    fun `확인하는 동안 다시 누른 확인은 같은 대기를 두 번 올리지 않는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()

        viewModel.confirmUpload()
        viewModel.confirmUpload()
        hold.complete(Unit)

        assertEquals(listOf("upload /local/a.txt"), transferRepo.calls)
    }

    @Test
    fun `충돌 확인을 취소하면 대기 목록을 돌려놓아 다시 확인할 수 있다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, listOf(domainItem("ftp://h/r/a.txt")))
        enterUpload()
        viewModel.confirmUpload()

        viewModel.resolveUploadConflict(ConflictResolution.CANCEL)

        assertEquals(SelectionState.Receiving(SelectMode.Upload, listOf(upload)), viewModel.remoteSelection.value)
        assertTrue(transferRepo.calls.isEmpty())
    }

    @Test
    fun `충돌 확인을 취소했을 때 다음 업로드가 원격 화면을 차지했으면 그대로 둔다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, listOf(domainItem("ftp://h/r/a.txt")))
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()
        viewModel.confirmUpload()
        enterUpload(other)
        hold.complete(Unit)

        viewModel.resolveUploadConflict(ConflictResolution.CANCEL)

        assertEquals(SelectionState.Receiving(SelectMode.Upload, listOf(other)), viewModel.remoteSelection.value)
        assertTrue(transferRepo.calls.isEmpty())
    }

    @Test
    fun `확인하는 동안 원격 세션이 끊기면 그 확인을 멈추고 올리지 않는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()
        viewModel.confirmUpload()

        connection.connectionState.value = RemoteConnectionState.Lost
        hold.complete(Unit)

        assertTrue(transferRepo.calls.isEmpty())
        assertNull(viewModel.uploadConflict.value)
        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
        assertTrue(!viewModel.uploadChecking.value)
    }

    @Test
    fun `확인하는 동안에만 확인 중으로 보인다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()

        viewModel.confirmUpload()
        assertTrue(viewModel.uploadChecking.value)

        hold.complete(Unit)
        assertTrue(!viewModel.uploadChecking.value)
    }

    @Test
    fun `확인을 멈추면 올리지 않고 대기 목록을 돌려놓는다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()
        viewModel.confirmUpload()

        viewModel.cancelUploadCheck()
        hold.complete(Unit)

        assertTrue(transferRepo.calls.isEmpty())
        assertTrue(!viewModel.uploadChecking.value)
        assertEquals(SelectionState.Receiving(SelectMode.Upload, listOf(upload)), viewModel.remoteSelection.value)
    }

    @Test
    fun `확인을 멈추면 가장 먼저 시작한 확인만 멈추고 다음 확인은 이어 간다`() {
        remoteNav.directoryListing.value = DirectoryListing.Loaded(remoteRequest, emptyList())
        val hold = CompletableDeferred<Unit>().also { folderEntries.hold = it }
        enterUpload()
        viewModel.confirmUpload()
        enterUpload(other)
        viewModel.confirmUpload()

        viewModel.cancelUploadCheck()
        assertTrue(viewModel.uploadChecking.value)
        hold.complete(Unit)

        assertEquals(listOf("upload /local/c.txt"), transferRepo.calls)
        assertEquals(SelectionState.Receiving(SelectMode.Upload, listOf(upload)), viewModel.remoteSelection.value)
    }

    /** 로컬 다중 선택 목록([item] 하나)을 업로드 대기 목록으로 옮긴다. */
    private fun enterUpload(item: FileHolderItem = upload) {
        viewModel.startLocalMultiSelect(item)
        viewModel.enterUploadMode()
    }

    /** [path]의 앱 레이어 파일 항목을 만든다. */
    private fun appItem(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)

    /** [path]의 domain 파일 항목을 만든다. */
    private fun domainItem(path: String) =
        DomainFileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)
}
