package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FTP_SCHEME
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpConnector
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.data.ftp.FtpTransferrer
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원격 스토리지 역할 인터페이스(연결·전송·변경)의 FTP 구현체.
 * @param session FTP 연결 상태를 노출하는 세션
 * @param connector FTP 접속·해제를 수행하는 커넥터
 * @param commandExecutor FTP 파일 명령을 실행하는 실행기
 * @param transferrer FTP 파일 전송을 수행하는 전송기
 */
@Singleton
class FtpRepositoryImpl @Inject constructor(
    session: FtpSession,
    private val connector: FtpConnector,
    private val commandExecutor: FtpCommandExecutor,
    private val transferrer: FtpTransferrer
) : RemoteConnectionRepository, RemoteFileTransferRepository, RemoteFileMutationRepository {

    override val isConnecting: StateFlow<Boolean> = session.isConnecting

    override val connectionState: StateFlow<RemoteConnectionState> = session.connectionState

    override suspend fun connect(item: RemoteStorageItem): ConnectResult =
        connector.connect(item.address, item.port, item.userId, item.password, item.encoding)

    override suspend fun disconnect() { connector.disconnect() }

    override fun rootPathOf(item: RemoteStorageItem): String =
        FTP_SCHEME + item.address.removePrefix(FTP_SCHEME)

    override suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean, onProgress: (ProgressState) -> Unit) =
        transferrer.download(remotePath, localPath, isDirectory, onProgress)

    override suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit) =
        transferrer.upload(localPath, remotePath, onProgress)

    override suspend fun rename(fromPath: String, toPath: String) = commandExecutor.rename(fromPath, toPath)

    override suspend fun deleteFile(path: String) = commandExecutor.deleteFile(path)

    override suspend fun makeDirectory(path: String) = commandExecutor.makeDirectory(path)

    override suspend fun removeDirectoryRecursive(path: String) = commandExecutor.removeDirectoryRecursive(path)
}
