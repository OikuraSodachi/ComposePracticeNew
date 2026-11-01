package com.todokanai.composepracticenew.ui.model

/**
 * 파일 검색 화면의 결과 표시 상태. 검색어 입력값은 TextField와 같은 프레임에 반영되도록 ViewModel의 Compose 상태로 따로 둔다.
 * @param results 지금까지 찾은 항목. 찾은 순서다
 * @param unreadableCount 읽지 못해 그 아래를 훑지 못한 항목 수. 0이 아니면 결과가 빠졌을 수 있다
 * @param status 검색 진행 상태
 */
data class SearchUiState(
    val results: List<SearchResult> = emptyList(),
    val unreadableCount: Int = 0,
    val status: SearchStatus = SearchStatus.Idle
)

/**
 * 검색으로 찾은 항목 하나.
 * @param item 찾은 항목
 * @param location 항목이 들어 있는 폴더의 경로. 상위가 없으면 null
 */
data class SearchResult(val item: FileHolderItem, val location: String?)

/** 파일 검색의 진행 상태. */
enum class SearchStatus {
    /** 검색어가 비어 있어 검색하지 않은 상태. */
    Idle,

    /** 검색어 입력이 멈추기를 기다리거나 저장소를 훑는 중인 상태. 결과는 찾는 대로 늘어난다. */
    Searching,

    /** 모든 저장소를 다 훑은 상태. */
    Finished,

    /** 검색할 저장소 볼륨을 찾지 못해 아무것도 훑지 않은 상태. 결과 없음과 구별해 알린다. */
    NoStorage
}
