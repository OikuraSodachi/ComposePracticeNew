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
}
