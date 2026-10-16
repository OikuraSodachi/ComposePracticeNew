package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository

/**
 * 원격 스토리지 연결과 초기 경로 이동을 담당하는 UseCase.
 * @param nav 원격 연결 기능을 지원하는 RemoteFileNavigatorRepository 구현체
 */
class RemoteConnectionUseCase(private val nav: RemoteFileNavigatorRepository) {
    /**
     * 원격 스토리지에 연결하고 루트 경로로 이동한다.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 성공이면 true, 실패이면 false
     */
    suspend fun connectAndNavigate(item: RemoteStorageItem): Boolean {
        val connected = nav.connectRemote(item)
        if (connected) nav.setRemotePath(item.address)
        return connected
    }
}
