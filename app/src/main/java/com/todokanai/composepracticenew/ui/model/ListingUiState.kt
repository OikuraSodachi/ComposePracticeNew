package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason

/**
 * 파일 목록 화면이 표시할 현재 디렉터리의 목록 상태. domain 목록 조회 상태를 화면용 항목으로 바꾼 앱 레이어 모델이다.
 * 조회 실패·조회 중을 빈 목록으로 바꾸지 않으므로, 화면은 "빈 폴더"를 [Loaded]의 항목이 비었을 때만 표시한다.
 */
@Immutable
sealed interface ListingUiState {

    /** 탐색할 경로가 아직 정해지지 않은 상태. */
    data object NoPath : ListingUiState

    /** 현재 경로의 목록을 조회하는 중인 상태. */
    data object Loading : ListingUiState

    /**
     * 현재 경로의 목록 조회를 마친 상태.
     * @param items 표시할 디렉터리 항목. 비어 있으면 실제로 빈 폴더다
     */
    data class Loaded(val items: List<FileHolderItem>) : ListingUiState

    /**
     * 현재 경로의 목록 조회에 실패한 상태.
     * @param reason 실패 종류 — 표시 문구를 정하는 데 쓴다
     * @param detail 서버 응답 등 실패의 상세 내용. 없으면 null
     */
    data class Failed(val reason: ListingFailureReason, val detail: String?) : ListingUiState

    companion object {
        /**
         * domain 목록 조회 상태를 화면용 상태로 바꾼다.
         * @param listing 변환할 domain 목록 조회 상태
         * @param isLocalFile 로컬 파일 목록이면 true — 항목의 URI 문자열을 채운다
         * @return 화면용 목록 상태
         */
        fun from(listing: DirectoryListing, isLocalFile: Boolean): ListingUiState = when (listing) {
            DirectoryListing.NoPath -> NoPath
            is DirectoryListing.Loading -> Loading
            is DirectoryListing.Loaded -> Loaded(FileHolderItem.fromList(listing.items, isLocalFile))
            is DirectoryListing.Failed -> Failed(listing.reason, listing.detail)
        }
    }
}
