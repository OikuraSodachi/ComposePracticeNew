package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.usecase.ConnectFailureAlertUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ConnectFailureAlerts]가 꺼낼 화면이 없을 때 맡긴 연결 실패도 남겨 두었다가 [ConnectFailureAlertUseCase.deliver]로 넘기는지 검증하는 단위 테스트(#371). */
class ConnectFailureAlertsTest {

    /** 검증 대상 알림함. */
    private val alerts = ConnectFailureAlerts()

    @Test
    fun `꺼낼 화면이 없을 때 맡긴 실패는 나중에 꺼내기 시작하면 맡긴 순서대로 넘긴다`() = runTest {
        alerts.report(ConnectFailureAlert.CONNECTION_FAILED)
        alerts.report(ConnectFailureAlert.RECONNECT_SETTINGS_UNREADABLE)
        val shown = mutableListOf<ConnectFailureAlert>()

        backgroundScope.launch { ConnectFailureAlertUseCase(alerts).deliver { shown += it } }
        runCurrent()

        assertEquals(listOf(ConnectFailureAlert.CONNECTION_FAILED, ConnectFailureAlert.RECONNECT_SETTINGS_UNREADABLE), shown)
        assertEquals(0, alerts.pending.value.size)
    }

    @Test
    fun `새 연결 시도를 시작하면 아직 알리지 않은 이전 실패를 뺀다`() {
        alerts.report(ConnectFailureAlert.CONNECTION_FAILED)

        alerts.connectionStarted()

        assertEquals(0, alerts.pending.value.size)
    }
}
