package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Contract for reading and updating the active directory navigation state. */
interface FileNavigatorRepository {
    val currentPath: StateFlow<String?>
    val dirTree: Flow<List<FileEntry>>
    val fileHolderItemList: StateFlow<List<FileHolderItem>>
    /** 경로가 원격인지 로컬인지를 구현체가 판단하여 이동한다. */
    suspend fun navigate(path: String)
    suspend fun setLocalPath(path: String)
    fun refresh()
    /** 주어진 경로의 부모 경로를 반환한다. 루트이면 null을 반환한다. */
    fun getParentPath(path: String): String?
    /** 원격 스토리지 서버에 연결하고 로그인한다. 성공 여부를 반환한다. */
    suspend fun connectRemote(address: String, port: Int, userId: String, password: String, encoding: String): Boolean
    /** 지정된 원격 경로로 탐색 위치를 이동시킨다. */
    suspend fun setRemotePath(path: String)
}
