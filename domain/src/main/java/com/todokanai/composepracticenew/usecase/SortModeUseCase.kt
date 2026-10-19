package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import kotlinx.coroutines.flow.Flow

/**
 * 정렬 모드 설정의 조회 및 저장을 담당하는 UseCase.
 * @param repo 정렬 모드 설정을 읽고 저장하는 SortPreferenceRepository 구현체
 */
class SortModeUseCase(private val repo: SortPreferenceRepository) {
    /** 현재 정렬 기준을 방출한다. */
    val sortBy: Flow<SortMode> = repo.sortBy

    /**
     * 정렬 기준을 저장한다.
     * @param value 저장할 정렬 기준
     * @return Unit
     */
    fun saveSortBy(value: SortMode) = repo.saveSortBy(value)
}
