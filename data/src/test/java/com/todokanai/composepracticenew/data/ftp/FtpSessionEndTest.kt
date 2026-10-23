package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * [FtpSession.endUnusableSession]이 취소된 코루틴에서 상태 잠금을 기다려야 할 때도 멈추지 않고 세션 종료를 끝내는지 검증하는 단위 테스트.
 * 멈추면 쓸 수 없는 세션이 로그인 상태로 남아 다음 명령이 어긋난 응답을 읽는다.
 */
class FtpSessionEndTest {

    /** 상태 잠금 경합을 재현하는 가짜 클라이언트. */
    private val client = FakeFtpClient()
    /** 검증 대상 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark()) } }

    @Test
    fun `취소된 코루틴에서 상태 잠금이 경합 중이어도 세션을 끊김 상태로 바꾸고 소켓을 닫는다`() {
        val lockHeld = CountDownLatch(1)
        val release = CountDownLatch(1)
        // 다른 스레드의 isReady가 상태 잠금을 쥔 채 isConnected에서 멈춘다
        client.onIsConnected = { lockHeld.countDown(); release.await() }
        val holder = thread { runBlocking { session.isReady() } }
        lockHeld.await()
        client.onIsConnected = {}

        runBlocking {
            val ending = launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().job.cancel()
                session.endUnusableSession()
            }
            release.countDown()
            ending.join()
        }
        holder.join()

        assertEquals(1, client.disconnectCalls)
        assertFalse(runBlocking { session.isReady() })
    }
}
