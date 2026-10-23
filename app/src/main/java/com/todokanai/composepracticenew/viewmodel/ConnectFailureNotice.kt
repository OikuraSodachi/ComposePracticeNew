package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.ConnectFailureReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 원격 연결 실패를 이유에 맞는 알림으로 바꾼다.
 * 다시 시도하면 될 수 있는 실패는 잠깐 보이는 이벤트로, 서버를 쓸 수 없는 실패는 사용자가 닫을 때까지 남는 다이얼로그 상태로 알린다.
 * ViewModel이 구성(composition)으로 보유해 다이얼로그 상태가 구성 변경 후에도 유지된다.
 */
class ConnectFailureNotice {

    private val _connectionFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 다시 시도하면 될 수 있는 연결 실패 이벤트. */
    val connectionFailed: SharedFlow<Unit> = _connectionFailed.asSharedFlow()

    private val _unreadableServer = MutableStateFlow(false)
    /** 서버의 파일 목록을 읽을 수 없어 연결을 종료했음을 알리는 다이얼로그를 보여야 하면 true. */
    val unreadableServer: StateFlow<Boolean> = _unreadableServer.asStateFlow()

    /**
     * 연결 실패를 [reason]에 맞는 알림으로 알린다.
     * @param reason 연결이 실패한 이유
     */
    fun report(reason: ConnectFailureReason) {
        when (reason) {
            ConnectFailureReason.CONNECTION_FAILED -> notifyConnectionFailed()
            ConnectFailureReason.LISTING_FORMAT_UNKNOWN -> _unreadableServer.value = true
        }
    }

    /** 다시 시도하면 될 수 있는 연결 실패를 알린다. */
    fun notifyConnectionFailed() {
        _connectionFailed.tryEmit(Unit)
    }

    /** 사용자가 서버를 읽을 수 없다는 다이얼로그를 닫았다. */
    fun dismissUnreadableServer() {
        _unreadableServer.value = false
    }
}
