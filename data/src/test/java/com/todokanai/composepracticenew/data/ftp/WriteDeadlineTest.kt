package com.todokanai.composepracticenew.data.ftp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * [WriteDeadline]이 쓰기가 끝나는 것과 제한 시간이 지나는 것 중 먼저 일어난 쪽만 결과로 삼는지 검증하는 단위 테스트.
 * 예약 취소가 늦어 감시 동작이 쓰기가 끝난 뒤에 실행되는 경우를 정해진 순서로 재현하려고, 예약한 동작을 테스트가 직접 실행한다.
 */
class WriteDeadlineTest {

    /** 예약한 동작을 실행하지 않고 넘겨주는 실행기. */
    private val scheduler = ManualScheduler()
    /** [WriteDeadline]이 제한 시간 동작을 실행한 횟수. */
    private var expiredCount = 0

    @After
    fun tearDown() {
        scheduler.shutdownNow()
    }

    @Test
    fun `쓰기가 끝나기 전에 제한 시간이 지나면 한 번 닫고 뒤에 끝난 쓰기도 제때 끝나지 않은 것으로 본다`() {
        val deadline = WriteDeadline(scheduler, TIMEOUT_MILLIS) { expiredCount++ }

        scheduler.runScheduled()
        deadline.finish()

        assertEquals(1, expiredCount)
        assertTrue(deadline.expired)
    }

    @Test
    fun `쓰기가 먼저 끝나면 그 뒤에 감시 동작이 실행돼도 닫지 않는다`() {
        val deadline = WriteDeadline(scheduler, TIMEOUT_MILLIS) { expiredCount++ }

        deadline.finish()
        scheduler.runScheduled()

        assertEquals(0, expiredCount)
        assertFalse(deadline.expired)
    }

    /**
     * 예약한 동작을 기억해 두고, 실제로는 아무 일도 하지 않는 동작을 예약하는 실행기.
     * 기억한 동작은 [runScheduled]로 원하는 순간에 실행한다 — 취소해도 지워지지 않아, 취소 전에 이미 실행되기 시작한 경우를 만든다.
     */
    private class ManualScheduler : ScheduledThreadPoolExecutor(1) {

        /** 마지막으로 예약된 동작. */
        private var scheduled: Runnable? = null

        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
            scheduled = command
            return super.schedule(Runnable {}, delay, unit)
        }

        /** 기억해 둔 동작을 이 스레드에서 실행한다. */
        fun runScheduled() {
            checkNotNull(scheduled).run()
        }
    }

    private companion object {
        /** 쓰기 한 번의 제한 시간(ms). 예약한 동작은 테스트가 직접 실행하므로 값 자체는 쓰이지 않는다. */
        const val TIMEOUT_MILLIS = 60_000L
    }
}
