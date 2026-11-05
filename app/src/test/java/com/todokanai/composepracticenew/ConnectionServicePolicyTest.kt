package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.service.ConnectionServicePolicy
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 연결 유지 서비스가 연결 상태를 따르는지 — 연결되거나 서버가 바뀔 때 시작을 요청하고, 연결되지 않은 상태가 되면 멈추는지 — 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionServicePolicyTest {

    /** 연결 상태를 테스트가 바꾸는 연결 저장소. `Connected("server")`로 시작한다. */
    private val connection = FakeConnectionRepository()

    /** 검증 대상 규칙. */
    private val policy = ConnectionServicePolicy(RemoteConnectionStateUseCase(connection))

    @Test
    fun `종료한 뒤 같은 서버에 다시 연결하면 다시 시작을 요청한다`() = runTest(UnconfinedTestDispatcher()) {
        val requests = collectStartRequests()

        connection.connectionState.value = RemoteConnectionState.Disconnected
        connection.connectionState.value = RemoteConnectionState.Connected("server")

        assertEquals(listOf("server", "server"), requests)
    }

    @Test
    fun `서버를 바꾸면 새 서버 이름으로 시작을 요청한다`() = runTest(UnconfinedTestDispatcher()) {
        val requests = collectStartRequests()

        connection.connectionState.value = RemoteConnectionState.Disconnected
        connection.connectionState.value = RemoteConnectionState.Connected("other")
        connection.connectionState.value = RemoteConnectionState.Connected("third")

        assertEquals(listOf("server", "other", "third"), requests)
    }

    @Test
    fun `끊기거나 종료하면 시작을 요청하지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val requests = collectStartRequests()

        connection.connectionState.value = RemoteConnectionState.Lost
        connection.connectionState.value = RemoteConnectionState.Disconnected

        assertEquals(listOf("server"), requests)
    }

    @Test
    fun `연결된 동안은 멈추지 않고 끊기면 멈춘다`() = runTest(UnconfinedTestDispatcher()) {
        val stop = launch { policy.awaitStop() }

        connection.connectionState.value = RemoteConnectionState.Connected("other")
        assertFalse(stop.isCompleted)

        connection.connectionState.value = RemoteConnectionState.Lost
        assertTrue(stop.isCompleted)
    }

    @Test
    fun `종료하면 멈춘다`() = runTest(UnconfinedTestDispatcher()) {
        val stop = launch { policy.awaitStop() }

        connection.connectionState.value = RemoteConnectionState.Disconnected

        assertTrue(stop.isCompleted)
    }

    @Test
    fun `이미 연결되지 않은 상태면 바로 멈춘다`() = runTest(UnconfinedTestDispatcher()) {
        connection.connectionState.value = RemoteConnectionState.Lost

        val stop = launch { policy.awaitStop() }

        assertTrue(stop.isCompleted)
    }

    @Test
    fun `보호가 끝나면 연결을 끊김으로 종료한다`() = runTest(UnconfinedTestDispatcher()) {
        policy.endProtection()

        assertEquals(RemoteConnectionState.Lost, connection.connectionState.value)
    }

    /** 시작 요청을 테스트가 끝날 때까지 모아 담는 목록을 돌려준다. */
    private fun TestScope.collectStartRequests(): List<String> {
        val requests = mutableListOf<String>()
        backgroundScope.launch { policy.startRequests.toList(requests) }
        return requests
    }
}
