package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.FakeConnectionRepository
import com.todokanai.composepracticenew.FakeNavigator
import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteStorageItem as DomainRemoteStorageItem
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteRootNavigator
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.ui.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem
import com.todokanai.composepracticenew.ui.model.StorageExit
import com.todokanai.composepracticenew.usecase.ConnectFailureReportUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 저장소 화면의 목록 이동 요청이 화면 방문에 묶이는지 — 떠난 뒤 끝난 열기는 요청하지 않고, 백그라운드에서는 요청을 지킨다(#542). */
class StorageViewModelExitTest {

    private val navigator = GatedNavigator()
    private val connection = GatedConnection()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `로컬 저장소를 열면 로컬 목록으로 넘어가라고 요청한다`() {
        val viewModel = viewModel()

        viewModel.openLocalStorage("/a")
        navigator.finish(NavigateResult.Navigated)

        assertEquals(StorageExit.LOCAL_LIST, viewModel.exitRequest.value)
    }

    @Test
    fun `열기가 끝나기 전에 화면을 떠나면 끝난 뒤 요청하지 않는다`() {
        val viewModel = viewModel()

        viewModel.openLocalStorage("/a")
        viewModel.onScreenLeft()
        navigator.finish(NavigateResult.Navigated)

        assertNull(viewModel.exitRequest.value)
    }

    @Test
    fun `연결 중에 화면을 떠나면 연결에 성공해도 원격 목록으로 넘어가라고 요청하지 않는다`() {
        val viewModel = viewModel()

        viewModel.setPath(remoteItem())
        viewModel.onScreenLeft()
        connection.finish(ConnectResult.Connected)

        assertNull(viewModel.exitRequest.value)
    }

    @Test
    fun `화면을 떠나면 처리하지 않은 요청을 버린다`() {
        val viewModel = viewModel()
        viewModel.openLocalStorage("/a")
        navigator.finish(NavigateResult.Navigated)

        viewModel.onScreenLeft()

        assertNull(viewModel.exitRequest.value)
    }

    @Test
    fun `떠난 뒤 다시 연 저장소는 이동을 요청한다`() {
        val viewModel = viewModel()
        viewModel.openLocalStorage("/a")
        viewModel.onScreenLeft()
        navigator.finish(NavigateResult.Navigated)

        viewModel.openLocalStorage("/b")
        navigator.finish(NavigateResult.Navigated)

        assertEquals(StorageExit.LOCAL_LIST, viewModel.exitRequest.value)
    }

    @Test
    fun `열기가 진행 중이면 다른 저장소 열기를 무시한다`() {
        val viewModel = viewModel()

        viewModel.openLocalStorage("/a")
        viewModel.setPath(remoteItem())

        assertEquals(listOf("/a"), navigator.requested)
        assertEquals(0, connection.attempts)
    }

    private fun viewModel() = StorageViewModel(
        storageVolumeUseCase = StorageVolumeUseCase(NoVolumes),
        remoteStorageUseCase = RemoteStorageUseCase(NoRemoteStorages),
        remoteConnectionUseCase = RemoteConnectionUseCase(connection, NoOpRootNavigator, NoOpFailureAlerts),
        connectionStateUseCase = RemoteConnectionStateUseCase(connection),
        localNavigator = navigator.let { FileNavigatorUseCase(it, it.paths) },
        connectFailureReportUseCase = ConnectFailureReportUseCase(NoOpFailureAlerts),
    )

    private fun remoteItem() = RemoteStorageItem(
        id = 1, name = "server", address = "host", port = 21, userId = "u", password = "p", settings = RemoteProtocolSettings.Ftp("UTF-8")
    )

    /** 이동을 [finish]할 때까지 기다리게 하는 탐색 저장소. 받은 경로를 기록한다. */
    private class GatedNavigator(val paths: FakeNavigator = FakeNavigator()) : FileNavigatorRepository by paths {
        /** 이동하라고 받은 경로. 받은 순서대로 쌓는다. */
        val requested = mutableListOf<String>()
        private var result = CompletableDeferred<NavigateResult>()

        override suspend fun navigate(path: String): NavigateResult {
            requested += path
            return result.await()
        }

        /** 기다리는 이동을 [outcome]으로 끝내고, 다음 이동은 다시 기다리게 한다. */
        fun finish(outcome: NavigateResult) {
            result.complete(outcome)
            result = CompletableDeferred()
        }
    }

    /** 연결을 [finish]할 때까지 기다리게 하는 연결 저장소. */
    private class GatedConnection(private val base: FakeConnectionRepository = FakeConnectionRepository()) : RemoteConnectionRepository by base {
        /** 연결을 시도한 수. */
        var attempts = 0
        private val result = CompletableDeferred<ConnectResult>()

        override suspend fun connect(item: DomainRemoteStorageItem): ConnectResult {
            attempts++
            return result.await()
        }

        /** 기다리는 연결을 [outcome]으로 끝낸다. */
        fun finish(outcome: ConnectResult) {
            result.complete(outcome)
        }
    }

    private object NoVolumes : StorageVolumeRepository {
        override val storageList = MutableStateFlow(emptyList<StorageVolumeInfo>())
        override fun load() {}
    }

    private object NoRemoteStorages : RemoteStorageRepository {
        override fun getAll() = flowOf(emptyList<DomainRemoteStorageItem>())
        override suspend fun insert(item: DomainRemoteStorageItem) {}
        override suspend fun delete(item: DomainRemoteStorageItem) {}
        override suspend fun getById(id: Long): DomainRemoteStorageItem? = null
    }

    private object NoOpRootNavigator : RemoteRootNavigator {
        override suspend fun navigateToRoot(item: DomainRemoteStorageItem) {}
    }

    private object NoOpFailureAlerts : ConnectFailureAlertReporter {
        override fun report(alert: ConnectFailureAlert) {}
        override fun connectionStarted() {}
    }
}
