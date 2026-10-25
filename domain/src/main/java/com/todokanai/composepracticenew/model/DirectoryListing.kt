package com.todokanai.composepracticenew.model

/**
 * 탐색 중인 디렉터리의 목록 조회 상태.
 * 목록이 어느 조회 요청의 것인지와 조회 결과를 한 값으로 묶어, 조회 실패·조회 중을 빈 폴더와 구분하고 다른 요청의 목록과 섞이지 않게 한다.
 */
sealed interface DirectoryListing {

    /** 탐색할 경로가 아직 정해지지 않은 상태. */
    data object NoPath : DirectoryListing

    /**
     * [request]의 목록을 조회하는 중인 상태.
     * @param request 조회 중인 요청
     * @param stale 같은 경로를 다시 조회하기 전의 목록. 화면이 비지 않도록 표시하는 데만 쓰며, 현재 목록으로 확인된 것이 아니다. 없으면 null
     */
    data class Loading(val request: ListingRequest, val stale: Loaded? = null) : DirectoryListing

    /**
     * [request]의 목록 조회를 마친 상태. [items]가 비어 있으면 저장소가 드러내는 항목이 없는 폴더다.
     * 알려진 한계(결과 타입에 담지 않기로 결정): 저장소가 목록에서 숨기는 항목(FTP 서버의 `.` 항목 등)은 [items]에 빠질 수 있으며, 이 값으로는 알 수 없다.
     * @param request 조회를 마친 요청
     * @param items 정렬된 디렉터리 항목
     */
    data class Loaded(val request: ListingRequest, val items: List<FileHolderItem>) : DirectoryListing

    /**
     * [request]의 목록 조회에 실패한 상태.
     * @param request 조회에 실패한 요청
     * @param reason 실패 종류
     * @param detail 서버 응답·시스템 메시지 등 실패의 상세 내용. 없으면 null
     */
    data class Failed(val request: ListingRequest, val reason: ListingFailureReason, val detail: String?) : DirectoryListing
}

/** 디렉터리 목록 조회 실패의 종류. 표시 문구는 app 레이어가 이 값에 대한 exhaustive `when`으로 정한다. */
enum class ListingFailureReason {
    /** 서버나 파일 시스템이 목록 조회를 거부했다(없는 경로·권한 없음 등). */
    UNREADABLE,
    /** 원격 서버와 연결되어 있지 않거나 연결이 끊겼다. */
    CONNECTION_LOST,
    /** 입출력 실패가 아닌 예상하지 못한 오류로 조회하지 못했다. 원인은 로그로 남긴다. */
    UNEXPECTED
}
