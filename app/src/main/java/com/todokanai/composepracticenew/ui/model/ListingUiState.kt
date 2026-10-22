package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason

/**
 * 파일 목록 화면이 표시할 현재 디렉터리의 목록 상태. domain 목록 조회 상태를 화면용 항목으로 바꾼 앱 레이어 모델이다.
 * 조회 실패·조회 중을 빈 목록으로 바꾸지 않으므로, 화면은 "빈 폴더"를 [Loaded]의 항목이 비었을 때만 표시한다.
 * 화면 표시 전용이다 — 이름 충돌 판정은 이 상태가 아니라 domain의 확인된 목록으로 한다.
 */
sealed interface ListingUiState {

    /** 탐색할 경로가 아직 정해지지 않은 상태. */
    data object NoPath : ListingUiState

    /** 현재 경로의 목록을 조회하는 중이고 보여줄 직전 목록도 없는 상태. */
    data object Loading : ListingUiState

    /**
     * 보여줄 목록이 있는 상태.
     * @param items 표시할 디렉터리 항목. 비어 있으면 빈 폴더다
     * @param isRefreshing 같은 경로를 다시 조회하는 중이라 [items]가 직전 목록이면 true. 이 동안에는 이름 충돌을 판정할 수 없어 작업이 거부된다
     */
    data class Loaded(val items: List<FileHolderItem>, val isRefreshing: Boolean = false) : ListingUiState

    /**
     * 현재 경로의 목록 조회에 실패한 상태.
     * @param reason 실패 종류 — 표시 문구를 정하는 데 쓴다
     * @param detail 서버 응답 등 실패의 상세 내용. 없으면 null
     */
    data class Failed(val reason: ListingFailureReason, val detail: String?) : ListingUiState
}

/**
 * domain 목록 조회 상태를 화면용 [ListingUiState]로 바꾸는 변환기.
 * 직전에 변환한 domain 목록과 같은 인스턴스가 다시 오면(다시 조회하는 동안의 직전 목록) 화면용 항목을 다시 만들지 않고 재사용한다.
 * 변환 결과를 기억하므로 하나의 수집 코루틴 안에서만 쓴다.
 * @param isLocalFile 로컬 파일 목록이면 true — 항목의 URI 문자열을 채운다
 */
class ListingUiStateMapper(private val isLocalFile: Boolean) {

    /** 마지막으로 변환한 domain 조회 완료 상태. */
    private var lastLoaded: DirectoryListing.Loaded? = null
    /** [lastLoaded]를 변환한 화면용 항목. */
    private var lastItems: List<FileHolderItem> = emptyList()

    /**
     * domain 목록 조회 상태를 화면용 상태로 바꾼다. 같은 경로를 다시 조회하는 중이면 직전 목록을 다시 조회 중 표시와 함께 보여준다.
     * @param listing 변환할 domain 목록 조회 상태
     * @return 화면용 목록 상태
     */
    fun map(listing: DirectoryListing): ListingUiState = when (listing) {
        DirectoryListing.NoPath -> ListingUiState.NoPath
        is DirectoryListing.Loading -> listing.stale?.let { ListingUiState.Loaded(itemsOf(it), isRefreshing = true) } ?: ListingUiState.Loading
        is DirectoryListing.Loaded -> ListingUiState.Loaded(itemsOf(listing))
        is DirectoryListing.Failed -> ListingUiState.Failed(listing.reason, listing.detail)
    }

    /**
     * domain 조회 완료 상태의 항목을 화면용 항목으로 바꾼다. 직전에 변환한 것과 같은 인스턴스면 그 결과를 재사용한다.
     * @param loaded 변환할 조회 완료 상태
     * @return 화면용 항목
     */
    private fun itemsOf(loaded: DirectoryListing.Loaded): List<FileHolderItem> {
        if (loaded !== lastLoaded) {
            lastItems = FileHolderItem.fromList(loaded.items, isLocalFile)
            lastLoaded = loaded
        }
        return lastItems
    }
}
