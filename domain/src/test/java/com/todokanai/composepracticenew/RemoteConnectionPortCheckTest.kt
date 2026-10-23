package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [RemoteConnectionUseCase.connectAndNavigate]가 포트가 범위를 벗어난 접속 정보로는 연결을 시도하지 않는지 검증하는 단위 테스트. */
class RemoteConnectionPortCheckTest {

    /** 연결 요청을 기록하는 가짜 저장소. */
    private val nav = RecordingNavigator()
    /** 검증 대상. */
    private val useCase = RemoteConnectionUseCase(nav)

    @Test
    fun `포트가 범위를 벗어나면 연결을 시도하지 않고 연결 실패로 알린다`() {
        listOf(0, 65536, -1).forEach { port ->
            val result = runBlocking { useCase.connectAndNavigate(itemWithPort(port)) }

            assertEquals("port=$port", ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED), result)
        }
        assertEquals(emptyList<Int>(), nav.connectedPorts)
    }

    @Test
    fun `포트가 범위 안이면 연결을 시도한다`() {
        listOf(1, 21, 65535).forEach { port -> runBlocking { useCase.connectAndNavigate(itemWithPort(port)) } }

        assertEquals(listOf(1, 21, 65535), nav.connectedPorts)
    }

    /**
     * [port]만 다른 접속 정보를 만든다.
     * @param port 접속 정보의 포트
     * @return 접속 정보
     */
    private fun itemWithPort(port: Int) = RemoteStorageItem(name = "nas", address = "192.168.0.1", port = port, userId = "", password = "")

    /** 연결 요청한 포트를 기록하고 언제나 연결에 성공하는 가짜 저장소. 탐색 기능은 쓰지 않는다. */
    private class RecordingNavigator : RemoteFileNavigatorRepository {
        /** 연결을 요청받은 포트. 받은 순서대로 쌓는다. */
        val connectedPorts = mutableListOf<Int>()

        override suspend fun connectRemote(item: RemoteStorageItem): ConnectResult {
            connectedPorts += item.port
            return ConnectResult.Connected
        }

        override suspend fun navigateToRemoteRoot(item: RemoteStorageItem) = Unit
        override val currentPath: StateFlow<String?> = MutableStateFlow(null)
        override val dirTree: Flow<List<FileEntry>> = emptyFlow()
        override val listingRequest: StateFlow<ListingRequest?> = MutableStateFlow(null)
        override val directoryListing: StateFlow<DirectoryListing> = MutableStateFlow(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) = Unit
        override fun refresh() = Unit
        override fun getParentPath(path: String): String? = null
    }
}
