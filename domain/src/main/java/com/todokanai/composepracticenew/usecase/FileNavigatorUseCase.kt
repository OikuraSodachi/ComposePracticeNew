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
     * 현재 조회 요청에 대해 조회를 마친 목록을 반환한다. 이름 충돌 판정처럼 목적지의 실제 항목이 필요한 판정 전용이다.
     * 조회 중(다시 조회하기 전 목록을 표시 중인 경우 포함)·조회 실패이거나 목록이 이전 요청의 것이면 실제 항목을 알 수 없으므로 null을 반환한다.
     * 판정한 목록의 경로([DirectoryListing.Loaded.request])를 그대로 실행 목적지로 쓰게 하려고 항목과 요청을 함께 반환한다.
     * 판정과 거부 알림을 [ConflictCheckUseCase] 한 곳에서만 하도록 domain 모듈 밖에는 공개하지 않는다.
     * @return 현재 요청의 조회 완료 상태. 확인된 목록이 없으면 null
     */
    internal fun verifiedListing(): DirectoryListing.Loaded? {
        // 목록을 요청보다 먼저 읽는다 — 목록은 요청이 바뀐 뒤에 만들어지므로, 이 순서에서 둘이 같으면 현재 요청의 목록이다
        val listing = nav.directoryListing.value as? DirectoryListing.Loaded ?: return null
        return listing.takeIf { it.request == nav.listingRequest.value }
    }

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
