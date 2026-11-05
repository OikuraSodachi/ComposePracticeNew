package com.todokanai.composepracticenew.service

import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 원격 연결 상태에서 연결 유지 서비스의 시작 요청과 멈출 때를 파생하는 규칙. MyApplication(시작)과 [FtpForegroundService](멈춤)가 이 규칙만 따른다.
 * 보호를 줄 수 없게 되면(시스템의 실행 시간 한도, 시작 거부) 연결도 끝낸다([endProtection]) — 보호 없이 남은 연결은 언제든 프로세스와 함께 사라질 수 있다.
 * @param connectionStateUseCase 서비스가 따를 원격 연결 상태를 제공하는 UseCase
 */
class ConnectionServicePolicy @Inject constructor(
    private val connectionStateUseCase: RemoteConnectionStateUseCase
) {

    /** 서비스를 시작(이미 실행 중이면 알림의 서버 이름 갱신)해야 할 때마다 알림에 표시할 서버 이름을 방출한다 — 연결되거나 연결한 서버가 바뀔 때. */
    val startRequests: Flow<String> = connectionStateUseCase.connectionState
        .map { (it as? RemoteConnectionState.Connected)?.serverName }
        .distinctUntilChanged()
        .filterNotNull()

    /**
     * 연결 상태가 연결되지 않은 상태(종료·전환·끊김)가 될 때까지 기다린다. 이미 그렇다면 바로 돌아온다.
     * @return Unit
     */
    suspend fun awaitStop() {
        connectionStateUseCase.connectionState.first { it !is RemoteConnectionState.Connected }
    }

    /**
     * 연결 유지 서비스가 보호를 줄 수 없게 됐을 때(시스템의 실행 시간 한도, 시작 거부), 연결을 끊김([RemoteConnectionState.Lost])으로 종료한다.
     * 사용자가 요청한 종료가 아니므로 화면은 끊긴 세션과 같이 다룬다(원격 화면을 나가고 전송 대기 상태를 버린다).
     * 연결된 세션이 없으면(연결 작업 중·이미 끊김·종료됨) 아무것도 끝내지 않는다. 연결 작업 중인 세션은 연결되면 서비스를 시작하므로, 그때 시작이 거부되면 다시 이 종료를 받는다.
     * @return 연결된 세션을 끝냈으면 true
     */
    suspend fun endProtection(): Boolean = connectionStateUseCase.drop()
}
