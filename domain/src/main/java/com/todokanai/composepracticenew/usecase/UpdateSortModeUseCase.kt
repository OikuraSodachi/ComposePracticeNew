package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.SortModeRepository

/** Persists the new sort mode preference; the file list updates reactively via DataStore Flow. */
class UpdateSortModeUseCase(
    private val sortModeRepo: SortModeRepository
) {
    operator fun invoke(sortMode: String) {
        sortModeRepo.saveSortBy(sortMode)
    }
}
