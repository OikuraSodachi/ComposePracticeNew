package com.todokanai.composepracticenew.model

/**
 * 디렉터리 목록 조회 요청. 같은 경로라도 이동·새로고침·재연결마다 [generation]이 달라 서로 다른 요청이 된다.
 * 목록 결과가 어느 요청의 것인지 이 값으로 구분해, 이전 요청의 목록이 현재 목록으로 쓰이지 않게 한다.
 * @param path 조회할 디렉터리 경로
 * @param generation 요청마다 증가하는 세대 번호
 */
data class ListingRequest(val path: String, val generation: Long)
