package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.usecase.ConnectionLossNoticeUseCase
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ConnectionLossNotices]의 끊김을 보여 주는 도중 새 연결을 시작하면 [ConnectionLossNoticeUseCase.deliver]가 보여 주기를 멈추는지 검증하는 단위 테스트. */
class ConnectionLossNoticesTest {

    /** 검증 대상 알림함. */
    private val notices = ConnectionLossNotices()

    @Test
    fun `보여 주는 도중 새 연결을 시작하면 끊김 표시를 거둔다`() = runTest {
        // Snackbar 대기열에서 기다리거나 떠 있는 끊김이 새 연결 뒤에 보이지 않도록
        var withdrawn = false
        notices.report()
        backgroundScope.launch {
            ConnectionLossNoticeUseCase(notices).deliver {
                try {
                    awaitCancellation()
                } finally {
                    withdrawn = true
                }
            }
        }
        runCurrent()

        notices.connectionStarted()
        runCurrent()

        assertTrue(withdrawn)
        assertEquals(0, notices.pending.value.size)
    }

    @Test
    fun `표시를 거둔 뒤 새로 끊기면 그 끊김은 알린다`() = runTest {
        val shown = mutableListOf<Int>()
        var showing = 0
        notices.report()
        backgroundScope.launch {
            ConnectionLossNoticeUseCase(notices).deliver {
                val n = ++showing
                if (n == 1) awaitCancellation()
                shown += n
            }
        }
        runCurrent()

        notices.connectionStarted()
        notices.report()
        runCurrent()

        assertEquals(listOf(2), shown)
    }
}
