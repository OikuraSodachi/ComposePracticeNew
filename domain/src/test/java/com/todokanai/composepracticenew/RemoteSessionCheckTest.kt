package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.usecase.RemoteSessionCheckUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RemoteSessionCheckUseCase]가 연결된 세션이 있을 때만 원격 화면 진입·업로드를 허용하고, 업로드를 거부할 때 알리는지 검증하는 단위 테스트. */
class RemoteSessionCheckTest {

    /** 넣은 거부 알림을 기록하는 알림함. */
    private val notices = RecordingOperationNotices()

    /** 연결 상태가 [state]인 검증 대상. */
    private fun checkWith(state: RemoteConnectionState) =
        RemoteSessionCheckUseCase(FakeRemoteConnection(state = state), notices)

    @Test
    fun `연결된 상태에서만 세션이 있다`() {
        assertTrue(checkWith(RemoteConnectionState.Connected("nas")).hasSession())
        assertFalse(checkWith(RemoteConnectionState.Disconnected).hasSession())
        assertFalse(checkWith(RemoteConnectionState.Lost).hasSession())
    }

    @Test
    fun `세션 유무가 바뀔 때만 차례로 내보낸다`() = runBlocking {
        val connection = FakeRemoteConnection(state = RemoteConnectionState.Connected("a"))
        val check = RemoteSessionCheckUseCase(connection, notices)
        val states = connection.connectionState as MutableStateFlow
        val emitted = mutableListOf<Boolean>()
        // Unconfined로 모아 상태를 넣는 즉시 받는다 — 연달아 바꿔도 중간 값이 합쳐져 사라지지 않도록
        val collecting = launch(Dispatchers.Unconfined) { check.sessionPresence.toList(emitted) }

        states.value = RemoteConnectionState.Connected("b")
        states.value = RemoteConnectionState.Lost
        states.value = RemoteConnectionState.Disconnected
        states.value = RemoteConnectionState.Connected("c")
        collecting.cancel()

        assertEquals(listOf(true, false, true), emitted)
    }

    @Test
    fun `연결된 상태면 업로드를 허용하고 알리지 않는다`() {
        assertTrue(checkWith(RemoteConnectionState.Connected("nas")).checkUploadEntry())
        assertEquals(emptyList<NoticeMessage>(), notices.failures)
    }

    @Test
    fun `연결한 적이 없거나 끊겼으면 업로드를 거부하고 세션 없음을 알린다`() {
        assertFalse(checkWith(RemoteConnectionState.Disconnected).checkUploadEntry())
        assertFalse(checkWith(RemoteConnectionState.Lost).checkUploadEntry())
        assertEquals(listOf(NoticeMessage.NoRemoteSession, NoticeMessage.NoRemoteSession), notices.failures)
    }
}
