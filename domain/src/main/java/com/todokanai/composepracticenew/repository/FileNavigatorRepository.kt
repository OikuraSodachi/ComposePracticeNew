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
     */
    suspend fun navigate(path: String): NavigateResult
    /** 현재 경로의 목록을 새 요청으로 다시 조회한다. */
    fun refresh()
    /**
     * 주어진 경로의 부모 경로를 반환한다. 루트이면 null을 반환한다.
     * 부모는 [path]보다 위의 경로여야 한다 — 경로 표시줄이 이 함수를 따라 루트(null)까지 올라가기 때문이다.
     */
    fun getParentPath(path: String): String?
    /** 주어진 디렉터리 경로의 표시 이름을 반환한다. 경로에서 이름을 꺼내는 규칙은 저장소마다 다르므로 구현체가 정한다. */
    fun displayNameOf(path: String): String
}
