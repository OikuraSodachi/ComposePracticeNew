package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * [FtpSession]이 연결된 세션을 끊김으로 바꿀 때만 끊김 알림을 넣고, 앱이 요청한 종료는 알리지 않으며,
 * 새 연결을 시작하면 아직 알리지 않은 끊김을 빼는지 검증하는 단위 테스트.
 */
class FtpSessionConnectionLossNoticeTest {

    /** 세션이 끊김을 넣는 알림함. */
    private val notices = ConnectionLossNotices()
    /** 검증 대상 세션. 로그인한 상태로 시작한다. */
    private val session = FtpSession(FakeFtpClient(), notices)
        .also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }

    @Test
    fun `연결된 세션의 명령이 응답을 읽지 못하면 끊김을 한 번 알린다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }

        assertEquals(1, notices.pending.value.size)
    }

    @Test
    fun `앱이 연결을 더 유지할 수 없어 끝낸 세션도 끊김을 알린다`() = runBlocking {
        session.dropConnected()

        assertEquals(1, notices.pending.value.size)
    }

    @Test
    fun `앱이 종료를 요청한 세션은 알리지 않는다`() = runBlocking {
        session.closeBypassingIoLock()
        // 종료로 소켓이 닫혀 진행 중이던 명령이 실패한 경우
        session.markConnectionDropped()

        assertEquals(0, notices.pending.value.size)
    }

    @Test
    fun `이미 끊긴 세션은 다시 알리지 않는다`() = runBlocking {
        session.markConnectionDropped()
        session.markConnectionDropped()
        session.dropConnected()

        assertEquals(1, notices.pending.value.size)
    }

    @Test
    fun `새 연결을 시작하면 아직 알리지 않은 끊김을 뺀다`() = runBlocking {
        session.markConnectionDropped()

        session.beginConnecting()

        assertEquals(0, notices.pending.value.size)
    }

    @Test
    fun `새 연결을 시작하면 보여 주는 중이던 끊김도 돌려놓지 않는다`() = runBlocking {
        session.markConnectionDropped()
        val shown = notices.pending.value.single().id
        notices.claim(shown)

        session.beginConnecting()
        notices.release(shown)

        assertEquals(0, notices.pending.value.size)
    }
}
