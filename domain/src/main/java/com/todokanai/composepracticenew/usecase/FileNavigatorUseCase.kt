package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
/**
 * 파일 탐색기의 네비게이션 상태를 읽고 조작하는 UseCase.
 * @param nav 파일 탐색기 네비게이션 상태를 관리하는 FileNavigatorRepository 구현체
 */
class FileNavigatorUseCase(private val nav: FileNavigatorRepository) {
    /** 현재 경로의 목록 조회 상태(조회 중·완료·실패). */
    val listing: StateFlow<DirectoryListing> = nav.directoryListing
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
     * 현재 경로의 목록 조회를 마쳤으면 그 항목들의 이름 집합을 반환한다. 충돌 판정처럼 이름만 필요한 소비자가 도메인 모델에 의존하지 않게 한다.
     * 조회 중·조회 실패이거나 목록이 아직 이전 경로의 것이면 목적지의 실제 항목을 알 수 없으므로 null을 반환한다.
     * @return 현재 디렉터리 항목 이름 집합. 확인된 목록이 없으면 null
     */
    fun loadedEntryNames(): Set<String>? = loadedListingAtCurrentPath()?.items?.mapTo(HashSet()) { it.name }

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

    /**
     * 현재 경로에 대해 조회를 마친 목록 상태를 반환한다.
     * @return 목록 상태가 [DirectoryListing.Loaded]이고 그 경로가 현재 경로와 같으면 그 상태, 아니면 null
     */
    private fun loadedListingAtCurrentPath(): DirectoryListing.Loaded? {
        val listing = nav.directoryListing.value as? DirectoryListing.Loaded ?: return null
        return listing.takeIf { it.path == nav.currentPath.value }
    }
}
