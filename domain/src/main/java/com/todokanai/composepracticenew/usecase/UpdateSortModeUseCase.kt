package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.SortModeRepository

/** Persists the new sort mode preference and immediately re-sorts the current file list. */
class UpdateSortModeUseCase(
    private val sortModeRepo: SortModeRepository,
    private val nav: FileNavigatorRepository
) {
    operator fun invoke(sortMode: String) {
        sortModeRepo.saveSortBy(sortMode)
        nav.setFileHolderItemList(sortMode)
    }
}
