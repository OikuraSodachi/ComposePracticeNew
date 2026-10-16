package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
/**
 * 파일 탐색기의 네비게이션 상태를 읽고 조작하는 UseCase.
 * @param nav 파일 탐색기 네비게이션 상태를 관리하는 FileNavigatorRepository 구현체
 */
class FileNavigatorUseCase(private val nav: FileNavigatorRepository) {
    val fileList: StateFlow<List<FileHolderItem>> = nav.fileHolderItemList
    val dirTree: Flow<List<FileEntry>> = nav.dirTree
    val currentPath: StateFlow<String?> = nav.currentPath

    /**
     * [path] 경로로 이동한다.
     * @param path 이동할 디렉터리의 절대 경로 또는 URI
     * @return Unit
     */
    suspend fun setPath(path: String) = nav.navigate(path)
    /**
     * 현재 디렉터리의 파일 목록을 강제로 새로 고침한다.
     * @return Unit
     */
    fun refresh() = nav.refresh()

    /**
     * 상위 디렉터리로 이동한다. 현재 경로가 루트이면 toRoot를 호출한다.
     * @param toRoot 상위 경로가 없을 때 호출되는 콜백 — currentPath가 null이면 호출되지 않는다
     * @return Unit
     */
    suspend fun navigateBack(toRoot: () -> Unit) {
        val path = nav.currentPath.value ?: return
        val parent = nav.getParentPath(path)
        if (parent == null) toRoot() else nav.navigate(parent)
    }
}
