package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import kotlinx.coroutines.flow.StateFlow

/** Contract for reading and updating the active directory navigation state. */
interface FileNavigatorRepository {
    /** 현재 탐색 중인 디렉터리 경로. 경로가 없으면(저장소 선택 화면) null이다. */
    val currentPath: StateFlow<String?>
    /** 현재 목록 조회 요청. 이동·새로고침·재연결마다 새 요청이 되며, 경로가 없으면 null이다. */
    val listingRequest: StateFlow<ListingRequest?>
    /** [listingRequest]의 목록 조회 상태. 조회 실패·조회 중을 빈 목록으로 바꾸지 않고 그대로 나타낸다. */
    val directoryListing: StateFlow<DirectoryListing>
    /**
     * 경로 이동의 단일 진입점. 경로 종류 판단과 접근성 검증은 구현체가 수행한다.
     * 검증에 실패하면 현재 경로를 바꾸지 않고 [NavigateResult.Rejected]를 돌려준다 — 호출자가 거부된 이동을 이동한 것으로 보지 않도록.
     * 거부는 구현체가 [OperationNoticeReporter]로 직접 알린다 — 호출자는 알리지 않고, 이동 뒤에 할 일이 있을 때만 결과를 본다.
     */
    suspend fun navigate(path: String): NavigateResult
    /** 현재 경로의 목록을 새 요청으로 다시 조회한다. */
    fun refresh()
}
