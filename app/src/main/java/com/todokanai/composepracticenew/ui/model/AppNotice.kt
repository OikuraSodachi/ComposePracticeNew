package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.PreferenceFailure

/**
 * 화면 하단 Snackbar로 알릴 앱 알림 하나(표시용). 알림함마다 종류를 하나씩 두어, 알림함을 더하면 문구와 보여 주는 시간을 정하는 when이 컴파일 오류로 알려 준다(#449).
 */
@Immutable
sealed interface AppNotice {

    /**
     * 설정 저장소의 실패.
     * @param failure 일어난 실패
     */
    data class PreferenceFailed(val failure: PreferenceFailure) : AppNotice

    /**
     * 파일 작업의 실패·거부·완료 메시지.
     * @param notice 알릴 메시지
     */
    data class Operation(val notice: OperationNotice) : AppNotice

    /** 원격 세션의 예기치 않은 끊김. */
    data object ConnectionLost : AppNotice

    /**
     * 다시 시도하면 될 수 있는 원격 연결 실패.
     * @param alert 일어난 연결 실패
     */
    data class ConnectFailed(val alert: ConnectFailureAlert) : AppNotice
}

/**
 * 알림 [notice]를 사용자에게 보일 문구로 바꾼다.
 * @param notice 알릴 알림
 * @return 보일 문구
 */
fun appNoticeTextOf(notice: AppNotice): UiText = when (notice) {
    is AppNotice.PreferenceFailed -> UiText.Resource(preferenceFailureMessage(notice.failure))
    is AppNotice.Operation -> notice.notice.message
    AppNotice.ConnectionLost -> UiText.Resource(R.string.notice_connection_lost)
    is AppNotice.ConnectFailed -> UiText.Resource(connectFailureMessage(notice.alert))
}

/**
 * 설정 저장소의 실패 [failure]를 사용자에게 알릴 문구로 바꾼다.
 * @param failure 일어난 실패
 * @return 알릴 문구의 문자열 리소스 id
 */
@StringRes
private fun preferenceFailureMessage(failure: PreferenceFailure): Int = when (failure) {
    PreferenceFailure.READ -> R.string.toast_preference_read_failed
    PreferenceFailure.SAVE -> R.string.toast_preference_save_failed
    PreferenceFailure.RESET -> R.string.toast_preference_reset
}

/**
 * 원격 연결 실패 [alert]를 사용자에게 알릴 문구로 바꾼다.
 * @param alert 일어난 연결 실패
 * @return 알릴 문구의 문자열 리소스 id
 */
@StringRes
private fun connectFailureMessage(alert: ConnectFailureAlert): Int = when (alert) {
    ConnectFailureAlert.CONNECTION_FAILED -> R.string.toast_connection_failed
    ConnectFailureAlert.RECONNECT_SETTINGS_UNREADABLE -> R.string.toast_reconnect_settings_unreadable
}
