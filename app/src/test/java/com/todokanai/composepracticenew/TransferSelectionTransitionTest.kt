package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.RemoteConnectionState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 다운로드·업로드로 화면 사이를 오갈 때 목적지 화면에 이전 선택이 남지 않고, 넘겨준 화면은 기본 모드로 돌아가는지 검증하는 단위 테스트(#252 리뷰).
 * 원격 세션이 끊기면 끊긴 세션에 기대는 전송 상태만 사라지는지, 연결된 원격 세션이 없으면 업로드 진입을 거부하는지도 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferSelectionTransitionTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 원격 세션 끊김을 테스트가 일으키는 연결 저장소. 연결된 상태로 시작한다. */
    private val connection = FakeConnectionRepository()
    /** 작업·업로드 진입 거부 알림을 기록하는 알림함. */
    private val notices = RecordingOperationNotices()
    /** 업로드 전 겹침 확인이 읽을 폴더 안 항목 이름. */
    private val folderEntries = FakeFolderEntryNamesRepository()
    private lateinit var viewModel: FileTransferCoordinatorViewModel

    private val localA = item("/local/a.txt")
    private val localB = item("/local/b.txt")
    private val remoteX = item("ftp://h/r/x.txt")
    private val remoteY = item("ftp://h/r/y.txt")

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
    fun `로컬에 선택이 남아 있어도 다운로드 목적지 화면에는 대기 목록만 있다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.addToLocalList(localB)
        viewModel.enterLocalOperation(SelectMode.LocalOperation.Copy)
        viewModel.startRemoteMultiSelect(remoteX)

        viewModel.enterDownloadMode()

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
        enterDownload(remoteX)

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

    @Test
    fun `로컬 화면에 있는 동안 원격 세션이 끊기면 다운로드 대기 목록이 사라진다`() {
        enterDownload(remoteX)

        connection.connectionState.value = RemoteConnectionState.Lost

        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `원격 세션이 끊기면 원격 화면의 선택과 업로드 대기 목록이 사라진다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.enterUploadMode()

        connection.connectionState.value = RemoteConnectionState.Lost

        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
    }

    @Test
    fun `원격 세션이 끊겨도 로컬 화면의 선택과 작업 상태는 남는다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.enterLocalOperation(SelectMode.LocalOperation.Move)

        connection.connectionState.value = RemoteConnectionState.Lost

        assertEquals(SelectionState.Operation(SelectMode.LocalOperation.Move, listOf(localA)), viewModel.localSelection.value)
    }

    @Test
    fun `앱이 연결을 종료한 것은 끊김이 아니므로 다운로드 대기 목록이 남는다`() {
        enterDownload(remoteX)

        connection.connectionState.value = RemoteConnectionState.Disconnected

        assertEquals(SelectionState.Receiving(SelectMode.Download, listOf(remoteX)), viewModel.localSelection.value)
    }

    @Test
    fun `로컬에 꺼낼 선택이 없으면 업로드 확인 단계로 들어가지 않고 원격 화면을 바꾸지 않는다`() {
        viewModel.startRemoteMultiSelect(remoteX)

        assertFalse(viewModel.enterUploadMode())

        assertEquals(SelectionState.MultiSelect(listOf(remoteX)), viewModel.remoteSelection.value)
    }

    @Test
    fun `원격에 꺼낼 선택이 없으면 다운로드 확인 단계로 들어가지 않고 로컬 화면을 바꾸지 않는다`() {
        viewModel.startLocalMultiSelect(localA)

        assertFalse(viewModel.enterDownloadMode())

        assertEquals(SelectionState.MultiSelect(listOf(localA)), viewModel.localSelection.value)
    }

    @Test
    fun `로컬 선택을 모두 해제했으면 업로드 확인 단계로 들어가지 않는다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.removeFromLocalList(localA)

        assertFalse(viewModel.enterUploadMode())

        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
    }

    @Test
    fun `연결한 적이 없으면 업로드 진입을 거부하고 알리며 로컬 선택은 그대로 둔다`() {
        connection.connectionState.value = RemoteConnectionState.Disconnected
        viewModel.startLocalMultiSelect(localA)

        assertFalse(viewModel.enterUploadMode())

        assertEquals(SelectionState.MultiSelect(listOf(localA)), viewModel.localSelection.value)
        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
        assertEquals(listOf(NoticeMessage.NoRemoteSession), notices.failures)
    }

    @Test
    fun `연결된 원격 세션이 있을 때만 원격 화면으로 갈 수 있다`() {
        assertTrue(viewModel.hasRemoteSession())

        connection.connectionState.value = RemoteConnectionState.Disconnected
        assertFalse(viewModel.hasRemoteSession())

        connection.connectionState.value = RemoteConnectionState.Lost
        assertFalse(viewModel.hasRemoteSession())
    }

    @Test
    fun `원격 선택을 모두 해제했으면 다운로드 확인 단계로 들어가지 않는다`() {
        viewModel.startRemoteMultiSelect(remoteX)
        viewModel.removeFromRemoteList(remoteX)

        assertFalse(viewModel.enterDownloadMode())

        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `로컬 선택을 꺼내면 로컬 화면은 기본 모드로 돌아가고 원격 화면은 그대로다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.enterLocalOperation(SelectMode.LocalOperation.Copy)
        viewModel.startRemoteMultiSelect(remoteX)

        val submitted = viewModel.submitLocalSelection()

        assertEquals(listOf(localA), submitted?.items)
        assertEquals(SelectionState.Default, viewModel.localSelection.value)
        assertEquals(SelectionState.MultiSelect(listOf(remoteX)), viewModel.remoteSelection.value)
    }

    @Test
    fun `원격 선택을 꺼내면 원격 화면은 기본 모드로 돌아가고 로컬 화면은 그대로다`() {
        viewModel.startRemoteMultiSelect(remoteX)
        viewModel.startLocalMultiSelect(localA)

        val submitted = viewModel.submitRemoteSelection()

        assertEquals(listOf(remoteX), submitted?.items)
        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
        assertEquals(SelectionState.MultiSelect(listOf(localA)), viewModel.localSelection.value)
    }

    @Test
    fun `선택이 한 항목이면 그 항목을 꺼내고 기본 모드로 돌아간다`() {
        viewModel.startLocalMultiSelect(localA)

        val submitted = viewModel.submitLocalItem()

        assertEquals(localA, submitted?.item)
        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `선택이 한 항목이 아니면 꺼내지 않고 선택을 그대로 둔다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.addToLocalList(localB)

        val submitted = viewModel.submitLocalItem()

        assertNull(submitted)
        assertEquals(SelectionState.MultiSelect(listOf(localA, localB)), viewModel.localSelection.value)
    }

    @Test
    fun `작업 목적지 확인 중에도 선택이 한 항목이면 그 항목을 꺼낸다`() {
        viewModel.startLocalMultiSelect(localA)
        viewModel.enterLocalOperation(SelectMode.LocalOperation.Copy)

        val submitted = viewModel.submitLocalItem()

        assertEquals(localA, submitted?.item)
        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `기본 모드에서는 꺼낼 항목이 없다`() {
        assertNull(viewModel.submitLocalItem())
        assertEquals(SelectionState.Default, viewModel.localSelection.value)
    }

    @Test
    fun `원격 선택이 한 항목이면 원격 화면만 기본 모드로 돌아가고 로컬 화면은 그대로다`() {
        viewModel.startRemoteMultiSelect(remoteX)
        viewModel.startLocalMultiSelect(localA)

        val submitted = viewModel.submitRemoteItem()

        assertEquals(remoteX, submitted?.item)
        assertEquals(SelectionState.Default, viewModel.remoteSelection.value)
        assertEquals(SelectionState.MultiSelect(listOf(localA)), viewModel.localSelection.value)
    }

    @Test
    fun `원격 세션이 이미 끊긴 뒤에 만들어도 크래시 없이 기본 모드로 시작한다`() {
        connection.connectionState.value = RemoteConnectionState.Lost

        val (created, uncaught) = capturingUncaught { createViewModel() }

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(SelectionState.Default, created.localSelection.value)
        assertEquals(SelectionState.Default, created.remoteSelection.value)
    }

    /**
     * 이 테스트의 가짜 저장소로 검증 대상을 만든다. 생성자 안에서 끊김 감시가 바로 실행되므로 만들 때의 [connection] 상태가 반영된다.
     * @return 새 ViewModel
     */
    private fun createViewModel(): FileTransferCoordinatorViewModel {
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, RecordingProgressReporter(), notices, GrantedProtection, UnexpectedQuestions, NoOpFailureRecorder, operationScope)
        return FileTransferCoordinatorViewModel(
            FakeNavigator().let { FileNavigatorUseCase(it, it) },
            FakeNavigator().let { FileNavigatorUseCase(it, it) },
            RemoteFileOperationUseCase(orchestrator, notices, RecordingTransferRepository(), NoOpMutationRepository, ConnectedSessionProvider, FileNameUseCase(NoStorageNameRules)),
            UploadConflictCheckUseCase(ConflictCheckUseCase(notices, ExactNameMatching), folderEntries, ExactNameMatching),
            RemoteConnectionStateUseCase(connection),
            RemoteSessionCheckUseCase(connection, notices)
        )
    }

    /**
     * [block]을 실행하는 동안 이 스레드에서 잡히지 않은 예외를 모은다. viewModelScope의 코루틴이 던진 예외는 생성자 밖으로 나오지 않고
     * 스레드의 처리기로 가므로(앱에서는 크래시), 테스트는 이렇게 모아서 확인한다.
     * @param block 실행할 코드
     * @return [block]의 결과와 그동안 잡히지 않은 예외
     */
    private fun <T> capturingUncaught(block: () -> T): Pair<T, List<Throwable>> {
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        val uncaught = mutableListOf<Throwable>()
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            return block() to uncaught
        } finally {
            thread.uncaughtExceptionHandler = previous
        }
    }

    /** 원격 화면에서 [item]을 다중 선택한 뒤 다운로드 대기 목록으로 로컬 화면에 넘긴다. */
    private fun enterDownload(item: FileHolderItem) {
        viewModel.startRemoteMultiSelect(item)
        viewModel.enterDownloadMode()
    }

    /** [path]의 앱 레이어 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
