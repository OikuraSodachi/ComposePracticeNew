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
    override suspend fun resolveNavigationPath(path: String): String =
        "ftp://${path.removePrefix("ftp://")}"
}
