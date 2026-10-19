package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.SortMode
import kotlinx.coroutines.flow.Flow

/** 파일 목록 정렬 기준 설정의 영속성 계약. */
interface SortPreferenceRepository {
    /** 현재 정렬 기준을 방출한다. 저장값이 없거나 알 수 없으면 [SortMode.BY_DEFAULT]. */
    val sortBy: Flow<SortMode>

    /** 정렬 기준 [value]를 저장한다. */
    fun saveSortBy(value: SortMode)
}
