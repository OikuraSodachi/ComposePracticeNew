package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.FtpRepository
import com.todokanai.composepracticenew.repository.LocalDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * FTP 연결 끊김 감지와 마지막 서버 자동 재연결을 담당하는 UseCase.
 * @param ftpRepo FTP 연결 상태를 관찰하는 FtpRepository
 * @param localDataRepo 마지막 연결 서버 정보를 조회하는 LocalDataRepository
 * @param fileNavigatorRepo 재연결 후 원격 경로로 이동하는 FileNavigatorRepository
 */
class FtpConnectionResilienceUseCase(
    private val ftpRepo: FtpRepository,
    private val localDataRepo: LocalDataRepository,
    private val fileNavigatorRepo: FileNavigatorRepository
) {
    /** isConnected가 true→false로 전환될 때마다 Unit을 방출한다. */
    val connectionDropped: Flow<Unit> = ftpRepo.isConnected
        .scan(Pair(false, false)) { acc, curr -> Pair(acc.second, curr) }
        .filter { (prev, curr) -> prev && !curr }
        .map { }

    /**
     * 마지막으로 접속한 서버에 재연결한다.
     * @return 성공 시 접속한 RemoteStorageItem, 저장된 서버가 없거나 실패 시 null
     */
    suspend fun reconnectLast(): RemoteStorageItem? {
        val id = localDataRepo.lastRemoteId() ?: return null
        val item = localDataRepo.getRemoteById(id) ?: return null
        val connected = fileNavigatorRepo.connectRemote(item.address, item.port, item.userId, item.password, item.encoding)
        if (connected) fileNavigatorRepo.setRemotePath(item.address)
        return if (connected) item else null
    }
}
