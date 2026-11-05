package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

/**
 * [FtpSession.connectionState]가 예기치 않은 끊김만 [RemoteConnectionState.Lost]로 남기고,
 * 앱이 요청한 종료·서버 전환·새 연결 시도와 구분하는지 검증하는 단위 테스트.
 * 끊김이 상태로 남아야 끊긴 뒤에 관찰을 시작한 쪽(다시 만들어진 화면 등)도 끊김을 안다.
 */
class FtpSessionConnectionStateTest {

    /** 세션이 소유하는 가짜 클라이언트. */
    private val client = FakeFtpClient()
    /** 검증 대상 세션. 로그인한 상태로 시작한다. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }

    @Test
    fun `연결된 세션의 명령이 응답을 읽지 못하면 끊김으로 남는다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }

        assertEquals(RemoteConnectionState.Lost, session.connectionState.value)
    }

    @Test
    fun `앱이 종료를 요청한 세션은 끊김이 아니다`() = runBlocking {
        session.closeBypassingIoLock()

        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun `앱이 연결을 더 유지할 수 없어 끝낸 세션은 끊김으로 남는다`() = runBlocking {
        session.closeBypassingIoLock(FtpSession.CloseKind.DROPPED)

        assertEquals(RemoteConnectionState.Lost, session.connectionState.value)
    }

    @Test
    fun `이미 종료한 세션을 끊김으로 끝내도 끊김이 아니다`() = runBlocking {
        session.closeBypassingIoLock()

        session.closeBypassingIoLock(FtpSession.CloseKind.DROPPED)

        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun `앱이 종료한 뒤 진행 중이던 명령이 실패해도 끊김으로 바뀌지 않는다`() = runBlocking {
        session.closeBypassingIoLock()

        session.markConnectionDropped()

        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun `다른 서버로 연결하려고 로그아웃하면 끊김이 아니다`() = runBlocking {
        session.markLoggedOut()

        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun `새 연결을 시도하면 이전 세션의 끊김 기록을 지운다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }

        session.beginConnecting()

        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun `다른 서버로 연결을 시도하는 동안 이전 세션의 명령이 실패해도 끊김이 아니다`() = runBlocking {
        session.beginConnecting()

        session.markConnectionDropped()

        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun `끊긴 세션에 다시 로그인하면 연결 상태가 된다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }
        session.beginConnecting()

        session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server")

        assertEquals(RemoteConnectionState.Connected("server"), session.connectionState.value)
    }

    @Test
    fun `다른 서버로 전환하면 연결 상태가 새로 연결한 서버를 가리킨다`() = runBlocking {
        // 연결 유지 서비스 알림이 연결 상태의 서버 이름을 보여 주므로, 전환 뒤에 이전 서버 이름이 남으면 안 된다
        session.beginConnecting()

        session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "other")

        assertEquals(RemoteConnectionState.Connected("other"), session.connectionState.value)
    }

    @Test
    fun `연결 작업이 표지를 읽은 뒤 종료가 요청됐으면 로그인 상태로 전환하지 않는다`() = runBlocking {
        session.beginConnecting()
        val closeMark = session.closeRequestMark()
        // 연결 작업이 서버 응답을 받은 뒤, 로그인 상태로 전환하기 전에 앱이 종료를 요청한다
        session.closeBypassingIoLock()

        assertFalse(session.markLoggedIn(closeMark, home = null, serverName = "server"))
        assertEquals(RemoteConnectionState.Disconnected, session.connectionState.value)
        assertFalse(session.isReady())
    }

    @Test
    fun `서버 거부처럼 결과로 돌아온 실패는 연결 상태를 바꾸지 않는다`() = runBlocking {
        session.withConnectedClient { IoResult.ItemFailed("550") }

        assertEquals(RemoteConnectionState.Connected("server"), session.connectionState.value)
    }
}
