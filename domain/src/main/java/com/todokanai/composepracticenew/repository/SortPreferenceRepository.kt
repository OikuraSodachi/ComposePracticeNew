package com.todokanai.composepracticenew.repository

import kotlinx.coroutines.flow.Flow

/** 파일 목록 정렬 기준 설정의 영속성 계약. */
interface SortPreferenceRepository {
    /** 현재 정렬 기준(Constants.BY_*)을 방출한다. */
    val sortBy: Flow<String>

    /** 정렬 기준 [value]를 저장한다. */
    fun saveSortBy(value: String)
}
