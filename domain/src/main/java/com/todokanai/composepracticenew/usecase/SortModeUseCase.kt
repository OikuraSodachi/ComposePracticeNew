package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.LocalDataRepository
import kotlinx.coroutines.flow.Flow

/**
 * 정렬 모드 설정의 조회 및 저장을 담당하는 UseCase.
 * @param repo 정렬 모드 설정을 읽고 저장하는 LocalDataRepository 구현체
 */
class SortModeUseCase(private val repo: LocalDataRepository) {
    val sortBy: Flow<String> = repo.sortBy
    fun saveSortBy(value: String) = repo.saveSortBy(value)
}
