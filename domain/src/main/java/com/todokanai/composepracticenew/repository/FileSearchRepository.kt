package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import kotlinx.coroutines.flow.Flow

/** 저장소 아래를 훑어 이름이 검색어와 맞는 항목을 찾는 저장소. */
interface FileSearchRepository {

    /**
     * [roots] 각각의 아래를 모두 훑어 이름이 [query]와 맞는 항목을 찾는다. 루트 자신은 결과에 넣지 않는다.
     * 읽지 못한 항목은 실패로 끝내지 않고 묶음의 unreadable로 알린 뒤 나머지를 계속 훑는다.
     * @param roots 훑을 루트 경로 목록
     * @param query 검색어
     * @return 찾는 대로 내보내는 결과 묶음 Flow. 모두 훑으면 완료되며, 수집을 취소하면 훑기를 멈춘다
     */
    fun search(roots: List<String>, query: SearchQuery): Flow<SearchBatch>
}
