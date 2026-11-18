package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 원격 스토리지 연결 상태 조회를 담당하는 UseCase.
 * 연결 종료는 [RemoteDisconnectUseCase](앱이 요청한 종료)와 [RemoteConnectionDropUseCase](보호를 잃었을 때의 끊김 종료)가 맡는다 — 상태만 읽는 쪽이 연결을 끝낼 수 없도록(#453).
 * 원격 파일 작업은 [RemoteFileOperationUseCase]가 단일 진입점으로 담당한다.
 * @param repo 원격 연결 수명주기를 관리하는 RemoteConnectionRepository 구현체
 */
class RemoteConnectionStateUseCase(repo: RemoteConnectionRepository) {

    /** 연결 또는 연결 해제 진행 중 여부를 방출한다. */
    val isConnecting: StateFlow<Boolean> = repo.isConnecting

    /** 서버와의 연결 상태. 예기치 않은 끊김은 다음 연결 시도나 종료 요청까지 [RemoteConnectionState.Lost]로 남는다. */
    val connectionState: StateFlow<RemoteConnectionState> = repo.connectionState
}
