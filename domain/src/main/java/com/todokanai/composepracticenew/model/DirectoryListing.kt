package com.todokanai.composepracticenew.model

/**
 * 탐색 중인 디렉터리의 목록 조회 상태.
 * 목록이 어느 경로의 것인지와 조회 결과를 한 값으로 묶어, 조회 실패·조회 중을 빈 폴더와 구분하고 다른 경로의 목록과 섞이지 않게 한다.
 */
sealed interface DirectoryListing {

    /** 탐색할 경로가 아직 정해지지 않은 상태. */
    data object NoPath : DirectoryListing

    /**
     * [path]의 목록을 조회하는 중인 상태. 이전 경로의 목록은 담지 않는다.
     * @param path 조회 중인 디렉터리 경로
     */
    data class Loading(val path: String) : DirectoryListing

    /**
     * [path]의 목록 조회를 마친 상태. [items]가 비어 있으면 실제로 빈 폴더다.
     * @param path 조회한 디렉터리 경로
     * @param items 정렬된 디렉터리 항목
     */
    data class Loaded(val path: String, val items: List<FileHolderItem>) : DirectoryListing

    /**
     * [path]의 목록 조회에 실패한 상태.
     * @param path 조회에 실패한 디렉터리 경로
     * @param reason 실패 종류
     * @param detail 서버 응답·시스템 메시지 등 실패의 상세 내용. 없으면 null
     */
    data class Failed(val path: String, val reason: ListingFailureReason, val detail: String?) : DirectoryListing
}

/** 디렉터리 목록 조회 실패의 종류. 표시 문구는 app 레이어가 이 값에 대한 exhaustive `when`으로 정한다. */
enum class ListingFailureReason {
    /** 서버나 파일 시스템이 목록 조회를 거부했다(없는 경로·권한 없음 등). */
    UNREADABLE,
    /** 원격 서버와 연결되어 있지 않거나 연결이 끊겼다. */
    CONNECTION_LOST
}
