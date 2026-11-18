package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [FtpSession]이 로그인된 세션이 예기치 않게 끊길 때만 끊김(onLost)을 한 번 알리고, 앱이 요청한 종료([FtpSession.close])는 끊김으로 보지 않으며,
 * 닫힌 세션은 다시 로그인 상태가 되지 않는지 검증하는 단위 테스트(#436 — 세션은 접속 한 번만 산다).
 */
class FtpSessionLossTest {

    /** 세션이 끊김을 알린 횟수. */
    private var lostCount = 0
    /** 검증 대상 세션. 로그인한 상태로 시작한다. */
    private val session = FtpSession(FakeFtpClient(), onLost = { lostCount++ })
        .also { runBlocking { it.markLoggedIn(home = null) } }

    @Test
    fun `로그인된 세션의 명령이 응답을 읽지 못하면 끊김을 한 번 알린다`() = runBlocking {
        val result = session.withConnectedClient<Unit> { throw IOException("read timed out") }

        assertEquals(IoResult.ConnectionLost(FailureReason.NoResponse("read timed out")), result)
        assertEquals(1, lostCount)
        assertFalse(session.isReady())
    }

    @Test
    fun `이미 끊긴 세션은 다시 알리지 않는다`() = runBlocking {
        session.withConnectedClient<Unit> { throw IOException("read timed out") }
        session.endUnusableSession()

        assertEquals(1, lostCount)
    }

    @Test
    fun `앱이 닫은 세션의 진행 중 명령이 실패해도 끊김으로 알리지 않는다`() = runBlocking {
        session.close()
        // 종료로 소켓이 닫혀 진행 중이던 명령이 실패한 경우
        session.endUnusableSession()

        assertEquals(0, lostCount)
    }

    @Test
    fun `서버 거부처럼 결과로 돌아온 실패는 끊김이 아니다`() = runBlocking {
        session.withConnectedClient { IoResult.ItemFailed(FailureReason.IoFailed("550")) }

        assertEquals(0, lostCount)
        assertTrue(session.isReady())
    }

    @Test
    fun `닫힌 세션은 다시 로그인 상태가 되지 않는다`() = runBlocking {
        // 여는 작업이 서버 응답을 받은 뒤, 로그인 상태로 전환하기 전에 앱이 종료를 요청한 경우
        session.close()

        assertFalse(session.markLoggedIn(home = null))
        assertFalse(session.isReady())
    }

    @Test
    fun `닫은 세션에는 명령을 보내지 않는다`() = runBlocking {
        session.close()
        var sent = false

        val result = session.withConnectedClient { sent = true; IoResult.Done }

        assertEquals(IoResult.ConnectionLost(FailureReason.NotConnected), result)
        assertFalse(sent)
    }
}
