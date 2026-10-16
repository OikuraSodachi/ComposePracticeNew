package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FTP_SCHEME
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.fileexplorer.FileEntry
import javax.inject.Inject

/**
 * ftp:// 스킴으로 시작하는 원격 경로를 처리하는 PathAwareFileSystem 구현체.
 * @param ftpCommands FTP 디렉터리 목록 조회를 담당하는 FtpCommandExecutor
 */
class FtpPathAwareFileSystem @Inject constructor(
    private val ftpCommands: FtpCommandExecutor
) : PathAwareFileSystem {
    override fun handles(path: String): Boolean = path.startsWith(FTP_SCHEME)
    override suspend fun listFiles(path: String): List<FileEntry> = ftpCommands.listFiles(path)
    override fun getParent(path: String): String? {
        val withoutScheme = path.removePrefix(FTP_SCHEME)
        return if (!withoutScheme.contains('/')) null
               else FTP_SCHEME + withoutScheme.substringBeforeLast('/')
    }
    // 원격 경로는 네트워크 I/O 없이 사전 접근성 검사를 할 수 없으므로 정규화만 수행하고 항상 non-null 반환
    override suspend fun resolveNavigationPath(path: String): String =
        FTP_SCHEME + path.removePrefix(FTP_SCHEME)
}
