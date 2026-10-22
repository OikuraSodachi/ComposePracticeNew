package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Contract for reading and updating the active directory navigation state. */
interface FileNavigatorRepository {
    val currentPath: StateFlow<String?>
    val dirTree: Flow<List<FileEntry>>
    /** 현재 목록 조회 요청. 이동·새로고침·재연결마다 새 요청이 되며, 경로가 없으면 null이다. */
    val listingRequest: StateFlow<ListingRequest?>
    /** [listingRequest]의 목록 조회 상태. 조회 실패·조회 중을 빈 목록으로 바꾸지 않고 그대로 나타낸다. */
    val directoryListing: StateFlow<DirectoryListing>
    /** 경로 이동의 단일 진입점. 경로 종류 판단과 접근성 검증은 구현체가 수행한다. */
    suspend fun navigate(path: String)
    /** 현재 경로의 목록을 새 요청으로 다시 조회한다. */
    fun refresh()
    /** 주어진 경로의 부모 경로를 반환한다. 루트이면 null을 반환한다. */
    fun getParentPath(path: String): String?
}
