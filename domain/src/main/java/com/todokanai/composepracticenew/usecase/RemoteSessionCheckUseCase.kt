package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 원격 화면으로 들어가기 전에 연결된 원격 세션이 있는지 확인하는 UseCase.
 * 원격 연결은 저장소 화면에서 사용자가 서버를 고를 때만 시작하므로, 연결 없이 원격 화면으로 가는 진입(하단 전환·업로드·원격 작업 진행 알림)은 여기서 막는다.
 * @param connection 연결 상태를 제공하는 RemoteConnectionRepository 구현체
 * @param notices 업로드 진입 거부를 화면이 알릴 때까지 맡는 OperationNoticeReporter
 */
class RemoteSessionCheckUseCase(
    private val connection: RemoteConnectionRepository,
    private val notices: OperationNoticeReporter
) {
    /**
     * 지금 연결된 원격 세션이 있는지 판별한다.
     * @return 연결 상태가 [RemoteConnectionState.Connected]면 true. 연결한 적 없음·종료됨·끊김이면 false
     */
    fun hasSession(): Boolean = isSession(connection.connectionState.value)

    /** 연결된 원격 세션이 있는지를 연결 상태가 바뀔 때마다 내보낸다. 원격 화면으로 가는 버튼을 세션이 있을 때만 누를 수 있게 하는 데 쓴다. 구독하면 현재 값을 바로 내보낸다. */
    val sessionPresence: Flow<Boolean> = connection.connectionState
        .map(::isSession)
        .distinctUntilChanged()

    /**
     * 업로드를 시작할 수 있는지 확인한다. 연결된 세션이 없으면 [NoticeMessage.NoRemoteSession]을 알리고 거부한다.
     * @return 연결된 세션이 있으면 true
     */
    fun checkUploadEntry(): Boolean {
        if (hasSession()) return true
        notices.report(NoticeMessage.NoRemoteSession)
        return false
    }

    /** [state]가 연결된 원격 세션인지 판별한다. 연결한 적 없음·종료됨·끊김은 세션이 아니다. */
    private fun isSession(state: RemoteConnectionState): Boolean = state is RemoteConnectionState.Connected
}
