package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.SharingStarted
import java.io.File

/** 로컬 파일 시스템만 탐색하는 FileNavigatorRepository 구현체. FTP 의존성이 없다. */
class LocalFileExplorerRepositoryImpl(
    converter: DataConverter,
    dsRepo: DataStoreRepository,
    initialPath: String? = null
) : BaseFileExplorerRepository(converter, dsRepo, SharingStarted.WhileSubscribed(5_000), initialPath) {

    override suspend fun listFiles(path: String): List<FileEntry> = localListFiles(path)

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) = setLocalPath(path)

    override suspend fun setLocalPath(path: String) {
        if (File(path).listFiles() != null) navigateTo(path)
    }

    // stub — not yet implemented
    override suspend fun connectRemote(address: String, port: Int, userId: String, password: String, encoding: String): Boolean = false

    // stub — not yet implemented
    override suspend fun setRemotePath(path: String) {}
}
