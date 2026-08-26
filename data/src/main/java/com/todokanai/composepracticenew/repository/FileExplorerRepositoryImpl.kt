package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.SharingStarted
import java.io.File

/** 로컬 및 FTP 원격 경로를 모두 탐색할 수 있는 FileNavigatorRepository 구현체. */
class FileExplorerRepositoryImpl(
    converter: DataConverter,
    dsRepo: DataStoreRepository,
    private val ftpFileSystem: FtpRepository,
    initialPath: String? = null
) : BaseFileExplorerRepository(converter, dsRepo, SharingStarted.Lazily, initialPath) {

    companion object {
        private const val TAG = "FileExplorerRepo"
    }

    override fun getParent(path: String): String? {
        if (!ftpFileSystem.isRemotePath(path)) return super.getParent(path)
        val withoutScheme = path.removePrefix("ftp://")
        return if (!withoutScheme.contains('/')) null
               else "ftp://" + withoutScheme.substringBeforeLast('/')
    }

    override suspend fun listFiles(path: String): List<FileEntry> {
        Log.d(TAG, "fileList 갱신 — path=$path")
        val files = if (ftpFileSystem.isRemotePath(path)) ftpFileSystem.listFiles(path)
                    else localListFiles(path)
        Log.d(TAG, "fileList 결과 — path=$path size=${files.size}")
        return files
    }

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) {
        if (ftpFileSystem.isRemotePath(path)) setRemotePath(path)
        else setLocalPath(path)
    }

    override suspend fun setLocalPath(path: String) {
        if (File(path).listFiles() != null) navigateTo(path)
    }

    override suspend fun connectRemote(address: String, port: Int, userId: String, password: String, encoding: String): Boolean =
        ftpFileSystem.connect(address, port, userId, password, encoding)

    override suspend fun setRemotePath(address: String) {
        navigateTo("ftp://${address.removePrefix("ftp://")}")
    }
}
