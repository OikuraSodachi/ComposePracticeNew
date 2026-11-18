package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository

/**
 * 앱이 연결을 더 유지할 수 없을 때(보호를 잃음) 연결된 원격 세션을 끊김([RemoteConnectionState.Lost])으로 종료하는 UseCase.
 * 끊김 알림이 나가므로 앱이 요청한 종료에는 쓰지 않는다 — 그쪽은 [RemoteDisconnectUseCase]다(#453).
 * @param repo 원격 연결 수명주기를 관리하는 RemoteConnectionRepository 구현체
 */
class RemoteConnectionDropUseCase(private val repo: RemoteConnectionRepository) {

    /**
     * 연결된 원격 세션을 끊김으로 종료한다. 연결된 세션이 없으면 아무 동작도 하지 않는다.
     * @return 연결된 세션을 끝냈으면 true
     */
    suspend fun drop(): Boolean = repo.drop()
}
