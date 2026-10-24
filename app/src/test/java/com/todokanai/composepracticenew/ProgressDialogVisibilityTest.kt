package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [MainViewModel]이 진행 중인 작업 목록·닫기·알림 클릭을 받아 진행률 다이얼로그에 띄울 작업 하나를 내보내는지 검증하는 단위 테스트.
 * 어느 작업을 띄울지의 세부 규칙은 ProgressDialogSelectionTest가 검증한다.
 * 띄울 작업 계산이 Dispatchers.Default에서 돌므로 기대하는 값이 나올 때까지 기다린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressDialogVisibilityTest {

    /** 테스트가 작업의 진행 상태를 직접 넣는 진행 저장소. */
    private val progressRepo = MutableProgressRepository()

    /** 검증 대상 ViewModel. 테스트마다 새로 만든다. */
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = MainViewModel(
            getStorageListUseCase = GetStorageListUseCase(EmptyStorageRepository),
            progressUseCase = ProgressUseCase(progressRepo),
            exitHandler = NoOpExitHandler
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `새 작업이 시작되면 가장 나중에 시작한 작업을 띄운다`() = runTest {
        backgroundScope.launch { viewModel.shownProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        awaitShownId(1)

        progressRepo.setProgressState(2, copyAt(0))

        awaitShownId(2)
    }

    @Test
    fun `닫으면 그 작업의 다이얼로그를 띄우지 않는다`() = runTest {
        backgroundScope.launch { viewModel.shownProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        awaitShownId(1)

        viewModel.dismissProgressDialog(1)

        awaitShownId(null)
    }

    @Test
    fun `띄운 작업이 끝나면 다이얼로그를 띄우지 않는다`() = runTest {
        backgroundScope.launch { viewModel.shownProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        awaitShownId(1)

        progressRepo.setProgressState(1, uploadAt(100))

        awaitShownId(null)
    }

    @Test
    fun `알림 클릭으로 그 작업의 다이얼로그를 띄우고 원격 작업이면 이동을 요청한다`() = runTest {
        backgroundScope.launch { viewModel.shownProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        progressRepo.setProgressState(2, copyAt(10))
        awaitShownId(2)
        viewModel.dismissProgressDialog(2)
        awaitShownId(null)

        viewModel.requestShowProgressDialog(FileOperationType.UPLOAD.id, instanceId = 1)

        awaitShownId(1)
        assertEquals(Unit, viewModel.remoteProgressNavigation.first())
    }

    @Test
    fun `로컬 작업 알림 클릭은 화면 이동을 요청하지 않는다`() = runTest {
        viewModel.requestShowProgressDialog(FileOperationType.COPY.id, instanceId = 1)

        assertNull(withTimeoutOrNull(1_000) { viewModel.remoteProgressNavigation.first() })
    }

    @Test
    fun `끝난 작업은 띄우지 않는다`() = runTest {
        backgroundScope.launch { viewModel.shownProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(50))
        progressRepo.setProgressState(2, uploadAt(100))

        awaitShownId(1)
    }

    /** 띄울 작업의 ID가 [expected]가 될 때까지 기다린다. 끝내 되지 않으면 runTest 시간 제한으로 실패한다. */
    private suspend fun awaitShownId(expected: Int?) {
        viewModel.shownProgress.first { it?.instanceId == expected }
    }

    private fun uploadAt(progress: Int) = ProgressState(progress = progress, operationType = FileOperationType.UPLOAD)

    private fun copyAt(progress: Int) = ProgressState(progress = progress, operationType = FileOperationType.COPY)
}

/** 진행 상태를 테스트가 직접 넣고 빼는 테스트용 진행 저장소. */
private class MutableProgressRepository : ProgressRepository {
    override val progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
    override val operationErrors: SharedFlow<String> = MutableSharedFlow()
    override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
    override fun setProgressState(instanceId: Int, state: ProgressState) {
        progressMap.value = progressMap.value + (instanceId to state)
    }
    override fun removeProgress(instanceId: Int) {
        progressMap.value = progressMap.value - instanceId
    }
    override fun emitError(message: String) {}
    override fun emitCompletion(message: String) {}
}

/** 스토리지가 없는 테스트용 스토리지 저장소. 이 테스트는 스토리지 목록을 쓰지 않는다. */
private object EmptyStorageRepository : StorageVolumeRepository {
    override val storageList: StateFlow<List<StorageVolumeInfo>> = MutableStateFlow(emptyList())
    override fun load() {}
}

/** 종료 이벤트를 발행하지 않는 테스트용 종료 핸들러. 이 테스트는 앱 종료를 쓰지 않는다. */
private object NoOpExitHandler : AppExitHandler {
    override val exitEvent: Flow<Unit> = emptyFlow()
    override fun execute() {}
}
