package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.ui.model.ConnectFailureDialog
import com.todokanai.composepracticenew.usecase.ConnectFailureReportUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 원격 연결 실패를 이유에 맞는 알림으로 바꾼다.
 * 다시 시도하면 될 수 있는 실패는 화면 호스트가 잠깐 보이는 알림함으로(#371 — 받을 화면이 없을 때 실패해도 잃지 않도록),
 * 서버나 포트를 쓸 수 없는 실패는 사용자가 닫을 때까지 남는 다이얼로그 상태로 알린다.
 * 다이얼로그는 한 번에 하나이며, 닫기 전에 다시 실패하면 마지막 실패의 다이얼로그로 바뀐다.
 * ViewModel이 구성(composition)으로 보유해 다이얼로그 상태가 구성 변경 후에도 유지된다.
 *
 * @param reportUseCase 다시 시도하면 될 수 있는 실패를 알림함에 맡기는 UseCase
 */
class ConnectFailureNotice(private val reportUseCase: ConnectFailureReportUseCase) {

    private val _dialog = MutableStateFlow<ConnectFailureDialog?>(null)
    /** 보여야 할 연결 실패 다이얼로그. 없으면 null. */
    val dialog: StateFlow<ConnectFailureDialog?> = _dialog.asStateFlow()

    /**
     * 연결 실패를 [reason]에 맞는 알림으로 알린다.
     * @param reason 연결이 실패한 이유
     */
    fun report(reason: ConnectFailureReason) {
        when (reason) {
            ConnectFailureReason.CONNECTION_FAILED -> notifyRetryable(ConnectFailureAlert.CONNECTION_FAILED)
            ConnectFailureReason.TIMED_OUT -> notifyRetryable(ConnectFailureAlert.TIMED_OUT)
            ConnectFailureReason.HOST_NOT_FOUND -> notifyRetryable(ConnectFailureAlert.HOST_NOT_FOUND)
            ConnectFailureReason.UNREACHABLE -> notifyRetryable(ConnectFailureAlert.UNREACHABLE)
            ConnectFailureReason.LOGIN_REFUSED -> notifyRetryable(ConnectFailureAlert.LOGIN_REFUSED)
            ConnectFailureReason.LISTING_FORMAT_UNKNOWN -> _dialog.value = ConnectFailureDialog.UNREADABLE_SERVER
            ConnectFailureReason.BINARY_TYPE_REFUSED -> _dialog.value = ConnectFailureDialog.BINARY_TYPE_REFUSED
            ConnectFailureReason.INVALID_PORT -> _dialog.value = ConnectFailureDialog.INVALID_PORT
        }
    }

    /**
     * 접속 정보·네트워크를 확인하고 다시 시도할 연결 실패를 알림함에 넣는다.
     * @param alert 알릴 연결 실패
     */
    private fun notifyRetryable(alert: ConnectFailureAlert) {
        reportUseCase.report(alert)
    }

    /** 사용자가 연결 실패 다이얼로그를 닫았다. */
    fun dismissDialog() {
        _dialog.value = null
    }
}
