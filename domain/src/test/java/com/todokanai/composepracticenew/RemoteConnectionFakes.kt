package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 연결 요청에 언제나 [result]로 답하고, 요청받은 접속 정보를 기록하는 가짜 연결 저장소. 연결 상태는 쓰지 않는다.
 * 루트 경로는 [rootPathFor]로 만든다.
 * @param result 연결 요청에 돌려줄 결과
 */
internal class FakeRemoteConnection(private val result: ConnectResult = ConnectResult.Connected) : RemoteConnectionRepository {
    /** 연결을 요청받은 접속 정보. 받은 순서대로 쌓는다. */
    val connected = mutableListOf<RemoteStorageItem>()

    override val isConnecting: StateFlow<Boolean> = MutableStateFlow(false)
    override val connectionState: StateFlow<RemoteConnectionState> = MutableStateFlow<RemoteConnectionState>(RemoteConnectionState.Disconnected)

    override suspend fun connect(item: RemoteStorageItem): ConnectResult {
        connected += item
        return result
    }

    override suspend fun disconnect() = Unit

    override suspend fun drop() = false

    override fun rootPathOf(item: RemoteStorageItem): String = rootPathFor(item)

    companion object {
        /**
         * 이 가짜 저장소가 [item] 서버의 루트로 돌려주는 경로.
         * @param item 원격 스토리지 접속 정보
         * @return 루트 경로
         */
        fun rootPathFor(item: RemoteStorageItem): String = "remote://${item.address}:${item.port}"
    }
}

/**
 * 이동 요청에 언제나 [result]로 답하고, 요청받은 경로를 기록하는 가짜 탐색 저장소. 목록 기능은 쓰지 않는다.
 * @param result 이동 요청에 돌려줄 결과
 */
internal class FakeNavigator(private val result: NavigateResult = NavigateResult.Navigated) : FileNavigatorRepository {
    /** 이동을 요청받은 경로. 받은 순서대로 쌓는다. */
    val navigated = mutableListOf<String>()

    override val currentPath: StateFlow<String?> = MutableStateFlow(null)
    override val listingRequest: StateFlow<ListingRequest?> = MutableStateFlow(null)
    override val directoryListing: StateFlow<DirectoryListing> = MutableStateFlow(DirectoryListing.NoPath)

    override suspend fun navigate(path: String): NavigateResult {
        navigated += path
        return result
    }

    override fun refresh() = Unit
    override fun getParentPath(path: String): String? = null
}
