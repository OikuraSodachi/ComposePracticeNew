package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SavedRemoteId
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 마지막 연결 서버 기록이 연결 성공([RemoteConnectionUseCase.connectAndNavigate])과 접속 정보 삭제([RemoteStorageUseCase.delete])에 묶여 남는지 검증하는 단위 테스트. */
class LastConnectionRecordingTest {

    /** 검증에 쓰는 접속 정보. */
    private val nas = RemoteStorageItem(id = 5L, name = "nas", address = "192.168.0.1", port = 21, userId = "", password = "")
    /** 가짜 저장소들이 받은 요청. 저장소를 가리지 않고 받은 순서대로 쌓아, 저장소 사이의 호출 순서를 검증한다. */
    private val calls = mutableListOf<String>()
    /** 기록·정리 요청을 [calls]에 쌓는 가짜 저장소. */
    private val lastConnection = RecordingLastConnection(calls)

    @Test
    fun `연결에 성공하면 그 서버를 마지막 연결 서버로 기록한다`() {
        runBlocking { RemoteConnectionUseCase(FixedNavigator(ConnectResult.Connected), lastConnection).connectAndNavigate(nas) }

        assertEquals(listOf("set 5"), calls)
    }

    @Test
    fun `연결에 실패하면 기록하지 않는다`() {
        val failed = ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)

        runBlocking { RemoteConnectionUseCase(FixedNavigator(failed), lastConnection).connectAndNavigate(nas) }

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `접속 정보를 지운 뒤에 그 서버를 가리키는 기록을 지운다`() {
        runBlocking { RemoteStorageUseCase(RecordingRemoteStorage(calls), lastConnection).delete(nas) }

        // 순서까지 본다 — 기록을 먼저 지우면 삭제가 실패했을 때 남은 서버로 재연결하지 못한다
        assertEquals(listOf("delete 5", "clearIf 5"), calls)
    }

    /**
     * 마지막 연결 서버의 기록·정리 요청을 [calls]에 쌓는 가짜 저장소. 읽기는 쓰지 않는다.
     * @param calls 받은 요청을 쌓을 목록
     */
    private class RecordingLastConnection(private val calls: MutableList<String>) : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = SavedRemoteId.None
        override fun setLastRemoteId(id: Long?) { calls += "set $id" }
        override fun clearLastRemoteIdIf(id: Long) { calls += "clearIf $id" }
    }

    /**
     * 연결 요청에 언제나 [result]로 답하는 가짜 저장소. 탐색 기능은 쓰지 않는다.
     * @param result 연결 요청에 돌려줄 결과
     */
    private class FixedNavigator(private val result: ConnectResult) : RemoteFileNavigatorRepository {
        override suspend fun connectRemote(item: RemoteStorageItem): ConnectResult = result
        override suspend fun navigateToRemoteRoot(item: RemoteStorageItem) = Unit
        override val currentPath: StateFlow<String?> = MutableStateFlow(null)
        override val listingRequest: StateFlow<ListingRequest?> = MutableStateFlow(null)
        override val directoryListing: StateFlow<DirectoryListing> = MutableStateFlow(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) = NavigateResult.Navigated
        override fun refresh() = Unit
        override fun getParentPath(path: String): String? = null
    }

    /**
     * 삭제 요청을 [calls]에 쌓는 가짜 접속 정보 저장소. 조회·추가는 쓰지 않는다.
     * @param calls 받은 요청을 쌓을 목록
     */
    private class RecordingRemoteStorage(private val calls: MutableList<String>) : RemoteStorageRepository {
        override fun getAll(): Flow<List<RemoteStorageItem>> = emptyFlow()
        override suspend fun insert(item: RemoteStorageItem) {}
        override suspend fun delete(item: RemoteStorageItem) { calls += "delete ${item.id}" }
        override suspend fun getById(id: Long): RemoteStorageItem? = null
    }
}
