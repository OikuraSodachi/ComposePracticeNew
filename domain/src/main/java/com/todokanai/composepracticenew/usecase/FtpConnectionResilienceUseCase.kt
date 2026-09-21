package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/** FTP 연결 끊김 감지와 마지막 서버 자동 재연결을 담당하는 UseCase. */
class FtpConnectionResilienceUseCase(
    private val ftpUseCase: FtpUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    private val fileNavigatorUseCase: FileNavigatorUseCase
) {
    /** isConnected가 true→false로 전환될 때마다 Unit을 방출한다. */
    val connectionDropped: Flow<Unit> = ftpUseCase.isConnected
        .scan(Pair(false, false)) { acc, curr -> Pair(acc.second, curr) }
        .filter { (prev, curr) -> prev && !curr }
        .map { }

    /**
     * 마지막으로 접속한 서버에 재연결한다.
     * @return 성공 시 접속한 RemoteStorageItem, 저장된 서버가 없거나 실패 시 null
     */
    suspend fun reconnectLast(): RemoteStorageItem? {
        val item = remoteStorageUseCase.getLastConnectedItem() ?: return null
        val connected = fileNavigatorUseCase.setPath(item)
        return if (connected) item else null
    }
}
