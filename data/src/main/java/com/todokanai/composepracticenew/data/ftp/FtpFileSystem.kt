package com.todokanai.composepracticenew.data.ftp

import com.todokanai.fileexplorer.FileEntry
import com.todokanai.composepracticenew.repository.FtpClientRepository
import javax.inject.Inject

/** FTPClient를 유지하며 FTP 원격 파일 시스템에 접근한다. 사용 전 connect()를 호출해야 한다. */
class FtpFileSystem @Inject constructor(
    private val connectionState: FtpConnectionState
) : FtpClientRepository {

    override suspend fun connect(address: String, port: Int, userId: String, password: String): Boolean =
        connectionState.connect(address, port, userId, password)

    override fun isRemotePath(path: String): Boolean = path.startsWith("ftp://")

    override suspend fun listFiles(path: String): List<FileEntry> =
        connectionState.listFiles(path)
}
