package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 원격 스토리지 연결 상태 조회와 연결 해제를 담당하는 UseCase.
 * 원격 파일 작업은 [RemoteFileOperationUseCase]가 단일 진입점으로 담당한다.
 * @param repo 원격 연결 수명주기를 관리하는 RemoteConnectionRepository 구현체
 */
class RemoteConnectionStateUseCase(private val repo: RemoteConnectionRepository) {

    /** 연결 또는 연결 해제 진행 중 여부를 방출한다. */
    val isConnecting: StateFlow<Boolean> = repo.isConnecting

    /** 서버와의 연결 상태. 예기치 않은 끊김은 다음 연결 시도나 종료 요청까지 [RemoteConnectionState.Lost]로 남는다. */
    val connectionState: StateFlow<RemoteConnectionState> = repo.connectionState

    /**
     * 원격 서버 연결을 해제한다.
     * @return Unit
     */
    suspend fun disconnect() = repo.disconnect()

    /**
     * 앱이 연결을 더 유지할 수 없어 원격 서버 연결을 끊김으로 종료한다. 연결돼 있었으면 [RemoteConnectionState.Lost]가 된다.
     * @return Unit
     */
    suspend fun drop() = repo.drop()
}
