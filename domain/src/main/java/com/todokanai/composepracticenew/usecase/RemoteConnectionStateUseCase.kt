package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 원격 스토리지 연결 진행 상태 조회와 연결 해제를 담당하는 UseCase.
 * 원격 파일 작업은 [RemoteFileOperationUseCase]가 단일 진입점으로 담당한다.
 * @param repo 원격 연결 수명주기를 관리하는 RemoteConnectionRepository 구현체
 */
class RemoteConnectionStateUseCase(private val repo: RemoteConnectionRepository) {

    /** 연결 또는 연결 해제 진행 중 여부를 방출한다. */
    val isConnecting: StateFlow<Boolean> = repo.isConnecting

    /**
     * 원격 서버 연결을 해제한다.
     * @return Unit
     */
    suspend fun disconnect() = repo.disconnect()
}
