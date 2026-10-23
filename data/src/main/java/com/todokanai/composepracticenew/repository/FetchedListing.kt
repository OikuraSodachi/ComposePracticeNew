package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ListingCompleteness
import com.todokanai.fileexplorer.FileEntry

/**
 * 조회한 디렉터리 항목과, 그 목록이 폴더의 항목을 빠짐없이 담았다고 확인됐는지 여부.
 * @param entries 조회한 항목(정렬 전)
 * @param completeness [entries]가 빠짐없이 확인된 목록인지 여부
 */
data class FetchedListing(val entries: List<FileEntry>, val completeness: ListingCompleteness) {
    companion object {
        /**
         * 저장소가 끝까지 돌려준 목록을 만든다.
         * @param entries 조회한 항목
         * @return 확인된 목록
         */
        fun complete(entries: List<FileEntry>): FetchedListing = FetchedListing(entries, ListingCompleteness.COMPLETE)
    }
}
