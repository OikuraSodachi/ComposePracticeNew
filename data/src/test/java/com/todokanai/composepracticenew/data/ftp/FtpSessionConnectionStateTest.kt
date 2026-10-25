package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * [FtpSession.connectionState]가 예기치 않은 끊김만 [RemoteConnectionState.LOST]로 남기고,
 * 앱이 요청한 종료·서버 전환·새 연결 시도와 구분하는지 검증하는 단위 테스트.
 * 끊김이 상태로 남아야 끊긴 뒤에 관찰을 시작한 쪽(다시 만들어진 화면 등)도 끊김을 안다.
 */
class FtpSessionConnectionStateTest {

    /** 세션이 소유하는 가짜 클라이언트. */
    private val client = FakeFtpClient()
    /** 검증 대상 세션. 로그인한 상태로 시작한다. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null) } }

    @Test
    fun `연결된 세션의 명령이 응답을 읽지 못하면 끊김으로 남는다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }

        assertEquals(RemoteConnectionState.LOST, session.connectionState.value)
    }

    @Test
    fun `앱이 종료를 요청한 세션은 끊김이 아니다`() = runBlocking {
        session.closeBypassingIoLock()

        assertEquals(RemoteConnectionState.DISCONNECTED, session.connectionState.value)
    }

    @Test
    fun `앱이 종료한 뒤 진행 중이던 명령이 실패해도 끊김으로 바뀌지 않는다`() = runBlocking {
        session.closeBypassingIoLock()

        session.markConnectionDropped()

        assertEquals(RemoteConnectionState.DISCONNECTED, session.connectionState.value)
    }

    @Test
    fun `다른 서버로 연결하려고 로그아웃하면 끊김이 아니다`() = runBlocking {
        session.markLoggedOut()

        assertEquals(RemoteConnectionState.DISCONNECTED, session.connectionState.value)
    }

    @Test
    fun `새 연결을 시도하면 이전 세션의 끊김 기록을 지운다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }

        session.beginConnecting()

        assertEquals(RemoteConnectionState.DISCONNECTED, session.connectionState.value)
    }

    @Test
    fun `다른 서버로 연결을 시도하는 동안 이전 세션의 명령이 실패해도 끊김이 아니다`() = runBlocking {
        session.beginConnecting()

        session.markConnectionDropped()

        assertEquals(RemoteConnectionState.DISCONNECTED, session.connectionState.value)
    }

    @Test
    fun `끊긴 세션에 다시 로그인하면 연결 상태가 된다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }
        session.beginConnecting()

        session.markLoggedIn(session.closeRequestMark(), home = null)

        assertEquals(RemoteConnectionState.CONNECTED, session.connectionState.value)
    }

    @Test
    fun `서버 거부처럼 결과로 돌아온 실패는 연결 상태를 바꾸지 않는다`() = runBlocking {
        session.withConnectedClient { IoResult.ItemFailed("550") }

        assertEquals(RemoteConnectionState.CONNECTED, session.connectionState.value)
    }
}
