package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 원격 세션이 끊긴 순간 어느 화면에 있든 알리고, 앱을 열 때 이미 끊겨 있던 세션이나 앱이 요청한 종료는 알리지 않는지 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionLostNoticeTest {

    /** 원격 세션 끊김을 테스트가 일으키는 연결 저장소. 연결된 상태로 시작한다. */
    private val connection = FakeConnectionRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `연결된 세션이 끊기면 알린다`() = runTest {
        val viewModel = createViewModel()

        connection.connectionState.value = RemoteConnectionState.LOST

        assertNotNull(withTimeoutOrNull(1_000) { viewModel.connectionLost.first() })
    }

    @Test
    fun `앱을 열 때 이미 끊겨 있던 세션은 알리지 않는다`() = runTest {
        connection.connectionState.value = RemoteConnectionState.LOST

        val viewModel = createViewModel()

        assertNull(withTimeoutOrNull(1_000) { viewModel.connectionLost.first() })
    }

    @Test
    fun `앱이 연결을 종료하면 알리지 않는다`() = runTest {
        val viewModel = createViewModel()

        connection.connectionState.value = RemoteConnectionState.DISCONNECTED

        assertNull(withTimeoutOrNull(1_000) { viewModel.connectionLost.first() })
    }

    /** [connection]의 지금 상태로 ViewModel을 만든다. 앱을 여는 시점에 해당한다. */
    private fun createViewModel() = MainViewModel(
        getStorageListUseCase = GetStorageListUseCase(EmptyStorageRepository),
        progressUseCase = ProgressUseCase(RecordingProgressRepository()),
        exitHandler = NoOpExitHandler,
        connectionStateUseCase = RemoteConnectionStateUseCase(connection)
    )
}
