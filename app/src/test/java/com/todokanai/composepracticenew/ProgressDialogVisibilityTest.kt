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
 * 진행률 다이얼로그의 닫음 상태가 Activity 스코프 [MainViewModel] 한 곳에만 있어,
 * 닫은 작업은 다시 표시하지 않고 새 작업·알림 클릭에서만 다시 표시하는지 검증하는 단위 테스트.
 * 표시 목록 계산이 Dispatchers.Default에서 돌므로 기대하는 값이 나올 때까지 기다린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressDialogVisibilityTest {

    private val progressRepo = MutableProgressRepository()
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
    fun `닫은 작업이 계속 진행 중이어도 다시 표시하지 않는다`() = runTest {
        backgroundScope.launch { viewModel.visibleProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        awaitVisibleIds(setOf(1))

        viewModel.dismissProgressDialog()

        awaitVisibleIds(emptySet())
    }

    @Test
    fun `닫은 뒤 새 작업이 시작되면 진행 중인 작업 전부를 다시 표시한다`() = runTest {
        backgroundScope.launch { viewModel.visibleProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        awaitVisibleIds(setOf(1))
        viewModel.dismissProgressDialog()
        awaitVisibleIds(emptySet())

        progressRepo.setProgressState(2, ProgressState(progress = 0, operationType = FileOperationType.COPY))

        awaitVisibleIds(setOf(1, 2))
    }

    @Test
    fun `알림 클릭으로 닫았던 다이얼로그를 다시 표시하고 원격 작업이면 이동을 요청한다`() = runTest {
        backgroundScope.launch { viewModel.visibleProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(30))
        awaitVisibleIds(setOf(1))
        viewModel.dismissProgressDialog()
        awaitVisibleIds(emptySet())

        viewModel.requestShowProgressDialog(FileOperationType.UPLOAD.id)

        awaitVisibleIds(setOf(1))
        assertEquals(Unit, viewModel.remoteProgressNavigation.first())
    }

    @Test
    fun `로컬 작업 알림 클릭은 화면 이동을 요청하지 않는다`() = runTest {
        viewModel.requestShowProgressDialog(FileOperationType.COPY.id)

        assertNull(withTimeoutOrNull(1_000) { viewModel.remoteProgressNavigation.first() })
    }

    @Test
    fun `완료된 작업은 표시하지 않는다`() = runTest {
        backgroundScope.launch { viewModel.visibleProgress.collect {} }
        progressRepo.setProgressState(1, uploadAt(100))
        progressRepo.setProgressState(2, uploadAt(50))

        awaitVisibleIds(setOf(2))
    }

    /** 표시 목록의 작업 ID가 [expected]가 될 때까지 기다린다. 끝내 되지 않으면 runTest 시간 제한으로 실패한다. */
    private suspend fun awaitVisibleIds(expected: Set<Int>) {
        viewModel.visibleProgress.first { it.keys == expected }
    }

    private fun uploadAt(progress: Int) = ProgressState(progress = progress, operationType = FileOperationType.UPLOAD)
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
