package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ConnectedSessionProvider
import com.todokanai.composepracticenew.ExactNameMatching
import com.todokanai.composepracticenew.FakeConnectionRepository
import com.todokanai.composepracticenew.FakeNavigator
import com.todokanai.composepracticenew.FixedMessages
import com.todokanai.composepracticenew.GrantedProtection
import com.todokanai.composepracticenew.InMemorySortPreference
import com.todokanai.composepracticenew.NoOpMutationRepository
import com.todokanai.composepracticenew.NoOpNotifier
import com.todokanai.composepracticenew.NoStorageNameRules
import com.todokanai.composepracticenew.RecordingOperationNotices
import com.todokanai.composepracticenew.RecordingProgressReporter
import com.todokanai.composepracticenew.RecordingTransferRepository
import com.todokanai.composepracticenew.NoOpFailureRecorder
import com.todokanai.composepracticenew.UnexpectedQuestions
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 원격 화면이 연결된 원격 세션이 없을 때(`Lost`·`Disconnected`, 들어올 때 이미 없어도) 나가야 함을 알리는지 검증하는 단위 테스트(#491 리뷰).
 * 이 화면은 연결하지 않으므로, 연결된 상태가 아니면 모두 세션 없음이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteFileListNoSessionTest {

    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 연결 상태를 테스트가 바꾸는 연결 저장소. 연결된 상태로 시작한다. */
    private val connection = FakeConnectionRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        operationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `연결 없이 만들어진 화면은 세션 없음으로 시작한다`() {
        connection.connectionState.value = RemoteConnectionState.Disconnected

        assertTrue(createViewModel().noSession.value)
    }

    @Test
    fun `연결된 세션이 끊기면 세션 없음이 된다`() {
        val viewModel = createViewModel()
        assertFalse(viewModel.noSession.value)

        connection.connectionState.value = RemoteConnectionState.Lost

        assertTrue(viewModel.noSession.value)
    }

    @Test
    fun `연결된 세션이 종료되면 세션 없음이 된다`() {
        val viewModel = createViewModel()

        connection.connectionState.value = RemoteConnectionState.Disconnected

        assertTrue(viewModel.noSession.value)
    }

    private fun createViewModel(): RemoteFileListViewModel {
        val notices = RecordingOperationNotices()
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, RecordingProgressReporter(), notices, GrantedProtection, UnexpectedQuestions, NoOpFailureRecorder, operationScope)
        return RemoteFileListViewModel(
            FakeNavigator().let { FileNavigatorUseCase(it, it) },
            SortModeUseCase(InMemorySortPreference()),
            RemoteFileOperationUseCase(orchestrator, notices, RecordingTransferRepository(), NoOpMutationRepository, ConnectedSessionProvider, FileNameUseCase(NoStorageNameRules)),
            RemoteConnectionStateUseCase(connection),
            ConflictCheckUseCase(notices, ExactNameMatching),
            FixedMessages
        )
    }
}
