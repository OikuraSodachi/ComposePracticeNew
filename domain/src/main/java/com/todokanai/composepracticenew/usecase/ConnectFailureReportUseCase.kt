package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter

/**
 * 연결 결과를 받은 화면이 다시 시도하면 될 수 있는 연결 실패를 알림함에 맡기기 위해 제공하는 UseCase.
 * @param reporter 연결 실패를 화면이 알릴 때까지 맡는 ConnectFailureAlertReporter
 */
class ConnectFailureReportUseCase(private val reporter: ConnectFailureAlertReporter) {
    /**
     * 연결 실패를 맡긴다. 화면 호스트가 화면이 보일 때 꺼내 알리므로, 맡긴 화면이 그사이 사라져도 알림이 남는다.
     * @param alert 알릴 연결 실패
     * @return Unit
     */
    fun report(alert: ConnectFailureAlert) = reporter.report(alert)
}
