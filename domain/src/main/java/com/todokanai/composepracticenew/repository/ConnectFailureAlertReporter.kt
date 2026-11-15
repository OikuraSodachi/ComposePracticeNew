package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectFailureAlert

/** 원격 연결 실패를 화면이 알릴 때까지 맡긴다. 맡긴 알림을 꺼내는 쪽 계약은 [ConnectFailureAlertRepository]다. */
interface ConnectFailureAlertReporter {
    /**
     * 연결 실패를 맡긴다. 호출 스레드에서 바로 반환한다.
     * @param alert 알릴 연결 실패
     */
    fun report(alert: ConnectFailureAlert)

    /** 새 연결 시도를 시작할 때 부른다. 아직 다 알리지 않은 이전 시도의 실패를 뺀다 — 새 연결이 실패한 것으로 보이지 않도록. */
    fun connectionStarted()
}
