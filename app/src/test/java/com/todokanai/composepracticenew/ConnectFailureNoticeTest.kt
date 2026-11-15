package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter
import com.todokanai.composepracticenew.usecase.ConnectFailureReportUseCase
import com.todokanai.composepracticenew.viewmodel.ConnectFailureNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertFalse(notice.unreadableServer.value)
        assertFalse(notice.invalidPort.value)
    }

    @Test
    fun `재연결할 서버가 없으면 접속 실패로 알림함에 맡긴다`() {
        notice.notifyConnectionFailed()

        assertEquals(listOf(ConnectFailureAlert.CONNECTION_FAILED), reporter.reported)
    }

    @Test
    fun `설정을 읽지 못한 재연결 실패는 그 이유로 알림함에 맡긴다`() {
        notice.notifyReconnectSettingsUnreadable()

        assertEquals(listOf(ConnectFailureAlert.RECONNECT_SETTINGS_UNREADABLE), reporter.reported)
    }

    @Test
    fun `목록을 읽을 수 없는 서버는 알림함에 맡기지 않고 닫을 때까지 다이얼로그 상태로 남는다`() {
        notice.report(ConnectFailureReason.LISTING_FORMAT_UNKNOWN)

        assertTrue(notice.unreadableServer.value)
        assertEquals(emptyList<ConnectFailureAlert>(), reporter.reported)

        notice.dismissUnreadableServer()

        assertFalse(notice.unreadableServer.value)
    }

    @Test
    fun `범위 밖 포트는 알림함에 맡기지 않고 닫을 때까지 다이얼로그 상태로 남는다`() {
        notice.report(ConnectFailureReason.INVALID_PORT)

        assertTrue(notice.invalidPort.value)
        assertFalse(notice.unreadableServer.value)
        assertEquals(emptyList<ConnectFailureAlert>(), reporter.reported)

        notice.dismissInvalidPort()

        assertFalse(notice.invalidPort.value)
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
