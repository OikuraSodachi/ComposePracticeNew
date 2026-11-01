package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.ListingRequest

/**
 * 화면에 보이는 항목이 나온 목록 조회 요청. 정렬을 바꾸거나 화면 상태를 다시 만들어도 같은 조회 결과면 같은 값이고,
 * 이동·새로고침·작업 후 갱신마다 달라진다 — 조회 결과 하나에 묶이는 화면 상태(폴더의 하위 항목 수 등)의 키로 쓴다.
 * @param path 조회한 디렉터리 경로
 * @param generation 조회 요청의 세대 번호. 클수록 나중 요청이다
 */
data class ListingKey(val path: String, val generation: Long) {

    companion object {
        /**
         * domain 목록 조회 요청을 화면용 키로 바꾼다.
         * @param request 바꿀 조회 요청
         * @return 그 요청의 화면용 키
         */
        fun from(request: ListingRequest): ListingKey = ListingKey(request.path, request.generation)
    }
}
