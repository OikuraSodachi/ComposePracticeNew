package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.RemoteConnectionRepository

/**
 * 앱이 요청한 원격 연결 종료를 담당하는 UseCase. 종료한 연결은 [com.todokanai.composepracticenew.model.RemoteConnectionState.Disconnected]가 된다.
 * 연결 상태 조회는 [RemoteConnectionStateUseCase], 보호를 잃었을 때의 끊김 종료는 [RemoteConnectionDropUseCase]가 맡는다(#453).
 * @param repo 원격 연결 수명주기를 관리하는 RemoteConnectionRepository 구현체
 */
class RemoteDisconnectUseCase(private val repo: RemoteConnectionRepository) {

    /**
     * 원격 서버 연결을 해제한다.
     * @return Unit
     */
    suspend fun disconnect() = repo.disconnect()
}
