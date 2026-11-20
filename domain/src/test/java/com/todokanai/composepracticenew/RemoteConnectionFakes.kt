package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteRootNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 연결 요청에 언제나 [result]로 답하고, 요청받은 접속 정보를 기록하는 가짜 연결 저장소. 연결 상태는 [state]에 머물며 연결 요청으로 바뀌지 않는다.
 * @param result 연결 요청에 돌려줄 결과
 * @param state 연결 상태로 보일 값
 */
internal class FakeRemoteConnection(
    private val result: ConnectResult = ConnectResult.Connected,
    state: RemoteConnectionState = RemoteConnectionState.Disconnected,
) : RemoteConnectionRepository {
    /** 연결을 요청받은 접속 정보. 받은 순서대로 쌓는다. */
    val connected = mutableListOf<RemoteStorageItem>()

    override val isConnecting: StateFlow<Boolean> = MutableStateFlow(false)
    override val connectionState: StateFlow<RemoteConnectionState> = MutableStateFlow(state)

    override suspend fun connect(item: RemoteStorageItem): ConnectResult {
        connected += item
        return result
    }

    override suspend fun disconnect() = Unit

    override suspend fun drop() = false
}

/**
 * 루트 이동을 요청받은 접속 정보를 기록하는 가짜 루트 이동. [failure]가 있으면 기록한 뒤 그 예외를 던진다.
 * @param failure 이동 요청에 던질 예외. null이면 이동한다
 */
internal class FakeRootNavigator(private val failure: Exception? = null) : RemoteRootNavigator {
    /** 루트 이동을 요청받은 접속 정보. 받은 순서대로 쌓는다. */
    val navigated = mutableListOf<RemoteStorageItem>()

    override suspend fun navigateToRoot(item: RemoteStorageItem) {
        navigated += item
        failure?.let { throw it }
    }
}

/** 이동 요청을 언제나 받아들이고, 요청받은 경로를 기록하는 가짜 탐색 저장소. 목록 기능은 쓰지 않는다. */
internal class FakeNavigator : FileNavigatorRepository {
    /** 이동을 요청받은 경로. 받은 순서대로 쌓는다. */
    val navigated = mutableListOf<String>()

    override val currentPath: StateFlow<String?> = MutableStateFlow(null)
    override val listingRequest: StateFlow<ListingRequest?> = MutableStateFlow(null)
    override val directoryListing: StateFlow<DirectoryListing> = MutableStateFlow(DirectoryListing.NoPath)

    override suspend fun navigate(path: String): NavigateResult {
        navigated += path
        return NavigateResult.Navigated
    }

    override fun refresh() = Unit
}

/** 새 연결 시도의 시작과 맡긴 연결 실패를 받은 순서대로 기록하는 가짜 연결 실패 알림함. */
internal class RecordingConnectFailureAlerts : ConnectFailureAlertReporter {
    /** 받은 호출. 연결 시작은 null, 맡긴 실패는 그 실패로 받은 순서대로 쌓는다. */
    val events = mutableListOf<ConnectFailureAlert?>()

    override fun report(alert: ConnectFailureAlert) {
        events += alert
    }

    override fun connectionStarted() {
        events += null
    }
}
