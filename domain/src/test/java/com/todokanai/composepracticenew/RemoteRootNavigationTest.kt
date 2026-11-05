package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SavedRemoteId
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [RemoteConnectionUseCase.connectAndNavigate]가 연결에 성공했을 때만 연결 저장소가 정한 루트 경로로 탐색 저장소를 이동시키고,
 * 그 이동이 거부되면 연결 성공을 알리지 않고 실패하는지 검증하는 단위 테스트(#229).
 */
class RemoteRootNavigationTest {

    /** 검증에 쓰는 접속 정보. */
    private val nas = RemoteStorageItem(id = 5L, name = "nas", address = "192.168.0.1", port = 21, userId = "", password = "")

    @Test
    fun `연결에 성공하면 연결 저장소가 정한 루트 경로로 이동한다`() {
        val nav = FakeNavigator()

        val result = runBlocking { RemoteConnectionUseCase(FakeRemoteConnection(), nav, NoOpLastConnection).connectAndNavigate(nas) }

        assertEquals(ConnectResult.Connected, result)
        assertEquals(listOf(FakeRemoteConnection.rootPathFor(nas)), nav.navigated)
    }

    @Test
    fun `연결에 실패하면 이동하지 않는다`() {
        val nav = FakeNavigator()
        val failed = ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)

        runBlocking { RemoteConnectionUseCase(FakeRemoteConnection(failed), nav, NoOpLastConnection).connectAndNavigate(nas) }

        assertEquals(emptyList<String>(), nav.navigated)
    }

    @Test
    fun `루트 경로로 이동을 거부당하면 연결 성공을 돌려주지 않고 구현 오류로 실패한다`() {
        val useCase = RemoteConnectionUseCase(FakeRemoteConnection(), FakeNavigator(NavigateResult.Rejected), NoOpLastConnection)

        assertThrows(IllegalStateException::class.java) { runBlocking { useCase.connectAndNavigate(nas) } }
    }

    /** 이동 검증이 마지막 연결 기록과 무관함을 전제로 한, 아무 동작도 하지 않는 테스트용 저장소. */
    private object NoOpLastConnection : LastConnectionRepository {
        override suspend fun lastRemoteId(): SavedRemoteId = SavedRemoteId.None
        override fun setLastRemoteId(id: Long?) {}
        override fun clearLastRemoteIdIf(id: Long) {}
    }
}
