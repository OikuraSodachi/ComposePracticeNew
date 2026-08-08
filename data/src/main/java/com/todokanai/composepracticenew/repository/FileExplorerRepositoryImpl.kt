package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.repository.FtpClientRepository
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Holds and manages the active directory navigation state (path, breadcrumb, file list). */
class FileExplorerRepositoryImpl(
    private val converter: DataConverter,
    private val dsRepo: DataStoreRepository,
    private val ftpFileSystem: FtpClientRepository,
    private val initialPath: String? = null
) : StorageRepository(), FileNavigatorRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    @Suppress("OPT_IN_USAGE")
    override val fileHolderItemList: StateFlow<List<FileHolderItem>> =
        combine(currentPath, dsRepo.sortBy, _refreshTrigger) { path, sortMode, _ -> path to sortMode }
            .flatMapLatest { (path, sortMode) ->
                flow {
                    val files = path?.let { listFiles(it) } ?: emptyList()
                    emit(converter.fileHolderItemList(files, sortMode))
                }
            }
            .stateIn(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    override fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    init {
        initialPath?.let { navigateTo(it) }
    }

    override fun getParent(path: String): String? {
        if (!ftpFileSystem.isRemotePath(path)) return super.getParent(path)
        val withoutScheme = path.removePrefix("ftp://")
        return if (!withoutScheme.contains('/')) null
               else "ftp://" + withoutScheme.substringBeforeLast('/')
    }

    override suspend fun listFiles(path: String): List<FileEntry> {
        val isRemote = ftpFileSystem.isRemotePath(path)
        return if (isRemote) {
            ftpFileSystem.listFiles(path)
        } else {
            File(path).listFiles()?.map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            } ?: emptyList()
        }
    }

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) {
        if (ftpFileSystem.isRemotePath(path)) setRemotePath(path)
        else setLocalPath(path)
    }

    override suspend fun setLocalPath(path: String) {
        if (File(path).listFiles() != null) navigateTo(path)
    }

    override suspend fun connectRemote(address: String, port: Int, userId: String, password: String): Boolean =
        ftpFileSystem.connect(address, port, userId, password)

    override suspend fun setRemotePath(address: String) {
        navigateTo("ftp://${address.removePrefix("ftp://")}")
    }
}