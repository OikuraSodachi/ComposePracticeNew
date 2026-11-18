package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.ConnectFailureAlertReporter
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteRootNavigator

/**
 * 원격 스토리지 연결과 초기 경로 이동을 담당하는 UseCase. 연결에 성공한 서버를 재연결 대상(마지막 연결 서버)으로 기록한다.
 * 연결은 [connection]이, 루트 이동은 [rootNavigator]가 맡으므로 탐색 계약은 연결 수명주기를 알지 않는다.
 * @param connection 서버 연결을 담당하는 RemoteConnectionRepository 구현체
 * @param rootNavigator 연결한 서버의 루트로 원격 화면의 탐색 위치를 옮기는 RemoteRootNavigator 구현체
 * @param lastConnectionRepo 연결에 성공한 서버 id를 기록하는 LastConnectionRepository 구현체
 * @param failureAlerts 연결 시도를 시작할 때 아직 알리지 않은 이전 시도의 실패를 빼는 연결 실패 알림함
 */
class RemoteConnectionUseCase(
    private val connection: RemoteConnectionRepository,
    private val rootNavigator: RemoteRootNavigator,
    private val lastConnectionRepo: LastConnectionRepository,
    private val failureAlerts: ConnectFailureAlertReporter
) {
    /**
     * 원격 스토리지에 연결하고, 연결되면 루트 경로로 이동한 뒤 그 서버를 마지막 연결 서버로 기록한다.
     * 기록을 연결 안에 두어, 어느 경로로 연결하든 재연결 대상이 연결한 서버와 어긋나지 않게 한다.
     * 포트가 범위를 벗어난 접속 정보(입력 검증이 생기기 전에 저장된 값 등)는 접속할 수 없으므로 연결을 시도하지 않고 [ConnectFailureReason.INVALID_PORT]로 알린다.
     * 시작할 때 아직 알리지 않은 이전 시도의 실패를 알림함([failureAlerts])에서 뺀다 — 이 시도가 성공한 뒤 이전 실패가 보여 이 연결이 실패한 것처럼 보이지 않도록.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 결과. 실패하면 기록하지 않고 이동하지 않는다
     */
    suspend fun connectAndNavigate(item: RemoteStorageItem): ConnectResult {
        failureAlerts.connectionStarted()
        if (item.port !in RemoteStorageItem.PORT_RANGE) return ConnectResult.Failed(ConnectFailureReason.INVALID_PORT)
        val result = connection.connect(item)
        if (result == ConnectResult.Connected) {
            rootNavigator.navigateToRoot(item)
            lastConnectionRepo.setLastRemoteId(item.id)
        }
        return result
    }
}
