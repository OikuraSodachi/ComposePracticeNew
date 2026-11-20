package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

/**
 * 취소된 코루틴에서 부른 [DataCommandRunner.cleanUpAfterCancel]이 세션 상태 잠금을 기다려야 해도 취소로 멈추지 않고
 * 정리 명령을 보내 그 결과를 돌려주는지 검증하는 단위 테스트(#433).
 */
class DataCommandRunnerCancelCleanupTest {

    /** 정리 명령의 거부 응답을 돌려줄 가짜 클라이언트. */
    private val client = FakeFtpClient()

    @Test
    fun `상태 잠금을 다른 작업이 쥐고 있어도 취소된 정리가 정리 명령을 보내고 결과를 돌려준다`() = runBlocking {
        val stateLock = Mutex(locked = true)
        val runner = DataCommandRunner(StateLockedSession(stateLock))
        var cleanupSent = false
        var result: CancelCleanup? = null

        val cancelled = launch {
            coroutineContext.job.cancel()
            result = runner.cleanUpAfterCancel(client) { cleanupSent = true; true }
        }
        // 정리가 상태 잠금을 기다리는 자리까지 가게 한 뒤 잠금을 푼다 — 다른 작업(연결·종료)이 잠금을 쥐었다 놓는 것을 흉내 낸다
        yield()
        stateLock.unlock()
        cancelled.join()

        assertTrue(cleanupSent)
        assertEquals(CancelCleanup.Done, result)
    }

    /**
     * 상태 확인이 [stateLock]을 거치는 세션. [FtpSession.isReady]가 상태 잠금을 쥐고 확인하는 것을 흉내 낸다.
     * @param stateLock 세션 상태 잠금
     */
    private class StateLockedSession(private val stateLock: Mutex) : FtpDataCommandSession {
        override fun <S : Closeable> openDataConnection(open: () -> S?): DataConnectionOpening<S> =
            error("정리 테스트는 데이터 연결을 열지 않는다")
        override suspend fun endUnusableSession() {}
        override suspend fun isReady(): Boolean = stateLock.withLock { true }
    }
}
