package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
/** 파일 탐색기의 네비게이션 상태를 읽고 조작하는 UseCase. */
class FileNavigatorUseCase(private val nav: FileNavigatorRepository) {
    val fileList: StateFlow<List<FileHolderItem>> = nav.fileHolderItemList
    val dirTree: Flow<List<FileEntry>> = nav.dirTree
    val currentPath: StateFlow<String?> = nav.currentPath

    suspend fun setPath(path: String) = nav.navigate(path)
    /** FTP 서버에 연결하고 루트 경로로 이동한다. 연결 성공 여부를 반환한다. */
    suspend fun setPath(item: RemoteStorageItem): Boolean {
        val connected = nav.connectRemote(item.address, item.port, item.userId, item.password)
        if (connected) nav.setRemotePath("ftp://${item.address.removePrefix("ftp://")}")
        return connected
    }
    fun refresh() = nav.refresh()

    /** 상위 디렉터리로 이동한다. 현재 경로가 루트이면 toRoot를 호출한다. */
    suspend fun navigateBack(toRoot: () -> Unit) {
        val path = nav.currentPath.value ?: return
        val parent = nav.getParentPath(path)
        if (parent == null) toRoot() else nav.navigate(parent)
    }
}
