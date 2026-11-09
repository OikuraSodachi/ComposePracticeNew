package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpConnectionStatus
import com.todokanai.composepracticenew.data.ftp.FtpConnector
import com.todokanai.composepracticenew.data.ftp.FtpTransferrer
import com.todokanai.composepracticenew.data.ftp.childFtpUri
import com.todokanai.composepracticenew.data.ftp.ftpUriOf
import com.todokanai.composepracticenew.data.ftp.siblingFtpUri
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File
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
class FtpRepositoryImpl @Inject internal constructor(
    session: FtpConnectionStatus,
    private val connector: FtpConnector,
    private val commandExecutor: FtpCommandExecutor,
    private val transferrer: FtpTransferrer
) : RemoteConnectionRepository, RemoteFileTransferRepository, RemoteFileMutationRepository {

    override val isConnecting: StateFlow<Boolean> = session.isConnecting

    override val connectionState: StateFlow<RemoteConnectionState> = session.connectionState

    override suspend fun connect(item: RemoteStorageItem): ConnectResult = when (val settings = item.settings) {
        is RemoteProtocolSettings.Ftp -> connector.connect(item.name, item.address, item.port, item.userId, item.password, settings.encoding)
    }

    override suspend fun disconnect() { connector.disconnect() }

    override suspend fun drop(): Boolean = connector.drop()

    override fun rootPathOf(item: RemoteStorageItem): String =
        ftpUriOf(item.address)

    override suspend fun download(remotePath: String, localDirPath: String, name: String, isDirectory: Boolean, onProgress: (ProgressState) -> Unit) =
        transferrer.download(remotePath, File(localDirPath, name).path, isDirectory, onProgress)

    override suspend fun upload(localPath: String, remoteDirPath: String, name: String, onProgress: (ProgressState) -> Unit) =
        transferrer.upload(localPath, childFtpUri(remoteDirPath, name), onProgress)

    override suspend fun rename(path: String, newName: String) = commandExecutor.rename(path, siblingFtpUri(path, newName))

    override suspend fun deleteFile(path: String) = commandExecutor.deleteFile(path)

    override suspend fun makeDirectory(parentPath: String, name: String) = commandExecutor.makeDirectory(childFtpUri(parentPath, name))

    override suspend fun removeDirectoryRecursive(path: String) = commandExecutor.removeDirectoryRecursive(path)
}
