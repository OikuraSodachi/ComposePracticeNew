package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.ReconnectResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SavedRemoteId
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.usecase.RemoteConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [RemoteConnectionResilienceUseCase.reconnectLast]가 저장된 서버 id를 읽지 못한 것을 저장된 서버가 없는 것과 구별해 돌려주는지 검증하는 단위 테스트. */
class ReconnectLastSavedIdTest {

    /** 저장소에 있는 서버. */
    private val nas = RemoteStorageItem(id = 5L, name = "nas", address = "192.168.0.1", port = 21, userId = "", password = "")

    @Test
    fun `설정을 읽지 못하면 저장된 서버 없음이 아니라 설정을 읽지 못함으로 돌려준다`() {
        assertEquals(ReconnectResult.SettingsUnreadable, reconnectWith(SavedRemoteId.Unreadable))
    }

    @Test
    fun `저장된 id가 없으면 저장된 서버 없음으로 돌려준다`() {
        assertEquals(ReconnectResult.NoSavedServer, reconnectWith(SavedRemoteId.None))
    }

    @Test
    fun `저장된 id의 서버에 다시 연결한다`() {
        assertEquals(ReconnectResult.Connected(nas), reconnectWith(SavedRemoteId.Saved(5L)))
    }

    /**
     * 마지막 서버 id를 [saved]로 읽는 저장소로 재연결한다.
     * @param saved 마지막 서버 id를 읽은 결과
     * @return 재연결 결과
     */
    private fun reconnectWith(saved: SavedRemoteId): ReconnectResult = runBlocking {
        val lastConnection = FixedLastConnection(saved)
        RemoteConnectionResilienceUseCase(lastConnection, SingleServerStorage(nas), RemoteConnectionUseCase(ConnectingNavigator, lastConnection))
            .reconnectLast()
    }

    /**
     * 마지막 서버 id를 언제나 [saved]로 읽는 가짜 저장소. 저장은 쓰지 않는다.
     * @param saved 돌려줄 읽기 결과
     */
    private class FixedLastConnection(private val saved: SavedRemoteId) : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = saved
        override fun setLastRemoteId(id: Long?) {}
        override fun clearLastRemoteIdIf(id: Long) {}
    }

    /**
     * 서버 [item] 하나만 갖는 가짜 접속 정보 저장소.
     * @param item 저장된 서버
     */
    private class SingleServerStorage(private val item: RemoteStorageItem) : RemoteStorageRepository {
        override fun getAll(): Flow<List<RemoteStorageItem>> = emptyFlow()
        override suspend fun insert(item: RemoteStorageItem) {}
        override suspend fun delete(item: RemoteStorageItem) {}
        override suspend fun getById(id: Long): RemoteStorageItem? = item.takeIf { it.id == id }
    }

    /** 언제나 연결에 성공하는 가짜 저장소. 탐색 기능은 쓰지 않는다. */
    private object ConnectingNavigator : RemoteFileNavigatorRepository {
        override suspend fun connectRemote(item: RemoteStorageItem): ConnectResult = ConnectResult.Connected
        override suspend fun navigateToRemoteRoot(item: RemoteStorageItem) = Unit
        override val currentPath: StateFlow<String?> = MutableStateFlow(null)
        override val listingRequest: StateFlow<ListingRequest?> = MutableStateFlow(null)
        override val directoryListing: StateFlow<DirectoryListing> = MutableStateFlow(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) = NavigateResult.Navigated
        override fun refresh() = Unit
        override fun getParentPath(path: String): String? = null
    }
}
