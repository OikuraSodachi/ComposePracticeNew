package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter
import com.todokanai.composepracticenew.ui.model.ConnectFailureDialog
import com.todokanai.composepracticenew.usecase.ConnectFailureReportUseCase
import com.todokanai.composepracticenew.viewmodel.ConnectFailureNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ConnectFailureNotice]가 다시 시도하면 될 수 있는 실패는 알림함에 맡기고, 서버나 포트를 쓸 수 없는 실패는 닫을 때까지 남는 다이얼로그 상태로 알리는지 검증하는 단위 테스트.
 */
class ConnectFailureNoticeTest {

    /** 맡긴 연결 실패를 기록하는 알림함. */
    private val reporter = RecordingConnectFailureAlerts()
    /** 검증 대상. */
    private val notice = ConnectFailureNotice(ConnectFailureReportUseCase(reporter))

    @Test
    fun `접속 실패는 다이얼로그 없이 알림함에 맡긴다`() {
        notice.report(ConnectFailureReason.CONNECTION_FAILED)

        assertEquals(listOf(ConnectFailureAlert.CONNECTION_FAILED), reporter.reported)
        assertNull(notice.dialog.value)
    }

    @Test
    fun `원인을 아는 접속 실패는 그 원인의 알림으로 알림함에 맡긴다`() {
        notice.report(ConnectFailureReason.TIMED_OUT)
        notice.report(ConnectFailureReason.HOST_NOT_FOUND)
        notice.report(ConnectFailureReason.UNREACHABLE)
        notice.report(ConnectFailureReason.LOGIN_REFUSED)

        val expected = listOf(ConnectFailureAlert.TIMED_OUT, ConnectFailureAlert.HOST_NOT_FOUND, ConnectFailureAlert.UNREACHABLE, ConnectFailureAlert.LOGIN_REFUSED)
        assertEquals(expected, reporter.reported)
        assertNull(notice.dialog.value)
    }

    @Test
    fun `목록을 읽을 수 없는 서버는 알림함에 맡기지 않고 닫을 때까지 다이얼로그 상태로 남는다`() {
        assertDialogUntilDismissed(ConnectFailureReason.LISTING_FORMAT_UNKNOWN, ConnectFailureDialog.UNREADABLE_SERVER)
    }

    @Test
    fun `바이너리 전송을 거부한 서버는 알림함에 맡기지 않고 닫을 때까지 다이얼로그 상태로 남는다`() {
        assertDialogUntilDismissed(ConnectFailureReason.BINARY_TYPE_REFUSED, ConnectFailureDialog.BINARY_TYPE_REFUSED)
    }

    @Test
    fun `범위 밖 포트는 알림함에 맡기지 않고 닫을 때까지 다이얼로그 상태로 남는다`() {
        assertDialogUntilDismissed(ConnectFailureReason.INVALID_PORT, ConnectFailureDialog.INVALID_PORT)
    }

    @Test
    fun `다이얼로그를 닫기 전에 다시 실패하면 마지막 실패의 다이얼로그로 바뀐다`() {
        notice.report(ConnectFailureReason.INVALID_PORT)
        notice.report(ConnectFailureReason.BINARY_TYPE_REFUSED)

        assertEquals(ConnectFailureDialog.BINARY_TYPE_REFUSED, notice.dialog.value)
    }

    /**
     * [reason]을 알리면 알림함에 맡기지 않고 [expected] 다이얼로그를 띄우며, 닫으면 사라지는지 확인한다.
     * @param reason 알릴 연결 실패 이유
     * @param expected 띄워야 할 다이얼로그
     */
    private fun assertDialogUntilDismissed(reason: ConnectFailureReason, expected: ConnectFailureDialog) {
        notice.report(reason)

        assertEquals(expected, notice.dialog.value)
        assertEquals(emptyList<ConnectFailureAlert>(), reporter.reported)

        notice.dismissDialog()

        assertNull(notice.dialog.value)
    }

    /** 맡긴 연결 실패를 순서대로 기록하는 테스트용 알림함. */
    private class RecordingConnectFailureAlerts : ConnectFailureAlertReporter {
        /** 맡긴 연결 실패(맡긴 순서). */
        val reported = mutableListOf<ConnectFailureAlert>()

        override fun report(alert: ConnectFailureAlert) {
            reported += alert
        }

        override fun connectionStarted() = Unit
    }
}
