package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository

/**
 * 원격 스토리지 연결과 초기 경로 이동을 담당하는 UseCase. 연결에 성공한 서버를 재연결 대상(마지막 연결 서버)으로 기록한다.
 * 연결은 [connection]이, 이동은 [nav]가 맡으므로 탐색 계약은 연결 수명주기를 알지 않는다.
 * @param connection 서버 연결과 루트 경로 계산을 담당하는 RemoteConnectionRepository 구현체
 * @param nav 원격 화면의 탐색 위치를 다루는 FileNavigatorRepository 구현체
 * @param lastConnectionRepo 연결에 성공한 서버 id를 기록하는 LastConnectionRepository 구현체
 */
class RemoteConnectionUseCase(
    private val connection: RemoteConnectionRepository,
    private val nav: FileNavigatorRepository,
    private val lastConnectionRepo: LastConnectionRepository
) {
    /**
     * 원격 스토리지에 연결하고, 연결되면 루트 경로로 이동한 뒤 그 서버를 마지막 연결 서버로 기록한다.
     * 기록을 연결 안에 두어, 어느 경로로 연결하든 재연결 대상이 연결한 서버와 어긋나지 않게 한다.
     * 포트가 범위를 벗어난 접속 정보(입력 검증이 생기기 전에 저장된 값 등)는 접속할 수 없으므로 연결을 시도하지 않고 실패로 알린다.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 결과. 실패하면 기록하지 않고 이동하지 않는다
     * @throws IllegalStateException 연결한 서버의 루트 경로로 이동할 수 없을 때. 루트 경로 형식과 탐색 저장소가 다루는 경로가 어긋난 배선 오류다
     */
    suspend fun connectAndNavigate(item: RemoteStorageItem): ConnectResult {
        if (item.port !in RemoteStorageItem.PORT_RANGE) return ConnectResult.Failed(ConnectFailureReason.CONNECTION_FAILED)
        val result = connection.connect(item)
        if (result == ConnectResult.Connected) {
            navigateToRoot(item)
            lastConnectionRepo.setLastRemoteId(item.id)
        }
        return result
    }

    /**
     * [item] 서버의 루트 경로로 이동한다. 거부되면 연결은 됐는데 이전 위치에 머물러 결과가 거짓이 되므로 구현 오류로 실패한다.
     * @param item 이동할 원격 스토리지 접속 정보
     */
    private suspend fun navigateToRoot(item: RemoteStorageItem) {
        val root = connection.rootPathOf(item)
        check(nav.navigate(root) == NavigateResult.Navigated) { "연결한 서버의 루트 경로를 탐색 저장소가 다루지 않는다: $root" }
    }
}
