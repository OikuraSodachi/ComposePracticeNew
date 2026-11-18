package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.ReconnectResult
import com.todokanai.composepracticenew.model.SavedRemoteId
import com.todokanai.composepracticenew.repository.LastConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteStorageRepository

/**
 * 마지막 서버 자동 재연결을 담당하는 UseCase. 끊김은 [RemoteConnectionStateUseCase.connectionState]로 관찰한다.
 * @param lastConnectionRepo 마지막 접속 서버 id를 조회하는 LastConnectionRepository
 * @param remoteStorageRepo 접속 정보를 조회하는 RemoteStorageRepository
 * @param remoteConnectionUseCase 연결과 경로 이동을 위임하는 RemoteConnectionUseCase
 */
class RemoteConnectionResilienceUseCase(
    private val lastConnectionRepo: LastConnectionRepository,
    private val remoteStorageRepo: RemoteStorageRepository,
    private val remoteConnectionUseCase: RemoteConnectionUseCase
) {
    /**
     * 마지막으로 접속한 서버에 재연결한다.
     * 연결은 [RemoteConnectionUseCase.connectAndNavigate]를 거치므로 재연결에 성공해도 같은 id를 마지막 연결 서버로 다시 기록한다(값이 같으면 저장소에 쓰지 않는다).
     * @return 재연결 결과. 저장된 서버 id가 없거나 그 서버 정보가 지워졌으면 [ReconnectResult.NoSavedServer],
     *   설정을 읽지 못했으면 [ReconnectResult.SettingsUnreadable]
     */
    suspend fun reconnectLast(): ReconnectResult {
        val id = when (val saved = lastConnectionRepo.lastRemoteId()) {
            is SavedRemoteId.Saved -> saved.id
            SavedRemoteId.None -> return ReconnectResult.NoSavedServer
            SavedRemoteId.Unreadable -> return ReconnectResult.SettingsUnreadable
        }
        val item = remoteStorageRepo.getById(id) ?: return ReconnectResult.NoSavedServer
        return when (val result = remoteConnectionUseCase.connectAndNavigate(item)) {
            ConnectResult.Connected -> ReconnectResult.Connected(item)
            is ConnectResult.Failed -> ReconnectResult.Failed(result.reason)
        }
    }
}
