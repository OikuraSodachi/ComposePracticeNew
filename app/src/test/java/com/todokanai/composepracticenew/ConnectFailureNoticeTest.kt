package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.viewmodel.ConnectFailureNotice
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConnectFailureNotice]가 다시 시도하면 될 수 있는 실패는 이벤트로, 서버를 쓸 수 없는 실패는 닫을 때까지 남는 다이얼로그 상태로 알리는지 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectFailureNoticeTest {

    /** 검증 대상. */
    private val notice = ConnectFailureNotice()

    @Test
    fun `접속 실패는 다이얼로그 없이 연결 실패 이벤트로 알린다`() = runTest {
        val events = mutableListOf<Unit>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            notice.connectionFailed.toList(events)
        }

        notice.report(ConnectFailureReason.CONNECTION_FAILED)

        assertEquals(1, events.size)
        assertFalse(notice.unreadableServer.value)
        collecting.cancel()
    }

    @Test
    fun `목록을 읽을 수 없는 서버는 이벤트 없이 닫을 때까지 다이얼로그 상태로 남는다`() = runTest {
        val events = mutableListOf<Unit>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            notice.connectionFailed.toList(events)
        }

        notice.report(ConnectFailureReason.LISTING_FORMAT_UNKNOWN)

        assertTrue(notice.unreadableServer.value)
        assertEquals(0, events.size)

        notice.dismissUnreadableServer()

        assertFalse(notice.unreadableServer.value)
        collecting.cancel()
    }
}
