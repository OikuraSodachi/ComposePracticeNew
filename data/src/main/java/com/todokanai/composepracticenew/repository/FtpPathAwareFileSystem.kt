package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry

/**
 * ftp:// 스킴으로 시작하는 원격 경로를 처리하는 PathAwareFileSystem 구현체.
 * @param ftpFiles FTP 파일 시스템 접근을 담당하는 FtpRepository
 */
class FtpPathAwareFileSystem(private val ftpFiles: FtpRepository) : PathAwareFileSystem {
    override fun handles(path: String): Boolean = path.startsWith("ftp://")
    override suspend fun listFiles(path: String): List<FileEntry> = ftpFiles.listFiles(path)
    override fun getParent(path: String): String? {
        val withoutScheme = path.removePrefix("ftp://")
        return if (!withoutScheme.contains('/')) null
               else "ftp://" + withoutScheme.substringBeforeLast('/')
    }
    // 원격 경로는 네트워크 I/O 없이 사전 접근성 검사를 할 수 없으므로 정규화만 수행하고 항상 non-null 반환
    override suspend fun resolveNavigationPath(path: String): String =
        "ftp://${path.removePrefix("ftp://")}"
}
