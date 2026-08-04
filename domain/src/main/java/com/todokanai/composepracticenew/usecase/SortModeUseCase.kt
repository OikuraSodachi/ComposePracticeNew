package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.SortModeRepository
import kotlinx.coroutines.flow.Flow

/** 정렬 모드 설정의 조회 및 저장을 담당하는 UseCase. */
class SortModeUseCase(private val repo: SortModeRepository) {
    val sortBy: Flow<String> = repo.sortBy
    fun saveSortBy(value: String) = repo.saveSortBy(value)
}
