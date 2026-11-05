package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileNavigatorRepository

/**
 * 원격 스토리지 연결과 초기 경로 이동을 담당하는 UseCase. 연결에 성공한 서버를 재연결 대상(마지막 연결 서버)으로 기록한다.
 * @param nav 원격 연결 기능을 지원하는 RemoteFileNavigatorRepository 구현체
 * @param lastConnectionRepo 연결에 성공한 서버 id를 기록하는 LastConnectionRepository 구현체
 */
class RemoteConnectionUseCase(
    private val nav: RemoteFileNavigatorRepository,
    private val lastConnectionRepo: LastConnectionRepository
) {
    /**
     * 원격 스토리지에 연결하고, 연결되면 그 서버를 마지막 연결 서버로 기록한 뒤 루트 경로로 이동한다.
     * 기록을 연결 안에 두어, 어느 경로로 연결하든 재연결 대상이 연결한 서버와 어긋나지 않게 한다.
     * 포트가 범위를 벗어난 접속 정보(입력 검증이 생기기 전에 저장된 값 등)는 접속할 수 없으므로 연결을 시도하지 않고 실패로 알린다.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 결과. 실패하면 기록하지 않고 이동하지 않는다
     */
    suspend fun connectAndNavigate(item: RemoteStorageItem): ConnectResult {
        if (item.port !in RemoteStorageItem.PORT_RANGE) return ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
        val result = nav.connectRemote(item)
        if (result == ConnectResult.Connected) {
            lastConnectionRepo.setLastRemoteId(item.id)
            nav.navigateToRemoteRoot(item)
        }
        return result
    }
}
