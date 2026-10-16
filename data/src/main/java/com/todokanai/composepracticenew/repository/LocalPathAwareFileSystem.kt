package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.fileexplorer.FileEntry

/**
 * 로컬 파일 시스템 경로(ftp:// 스킴 없는 절대 경로)를 처리하는 PathAwareFileSystem 구현체.
 * @param localFiles 로컬 디렉터리 읽기를 담당하는 LocalFileDataSource
 */
class LocalPathAwareFileSystem(private val localFiles: LocalFileDataSource) : PathAwareFileSystem {
    override fun handles(path: String): Boolean = !path.startsWith("ftp://")
    override suspend fun listFiles(path: String): List<FileEntry> = localFiles.listFiles(path)
    override fun getParent(path: String): String? =
        path.substringBeforeLast('/').takeIf { it.isNotEmpty() && it != path }
    override suspend fun resolveNavigationPath(path: String): String? =
        if (localFiles.canRead(path)) path else null
}
