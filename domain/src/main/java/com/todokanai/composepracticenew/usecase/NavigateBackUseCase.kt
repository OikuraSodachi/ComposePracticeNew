package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileNavigatorRepository

/** Navigates to the parent directory, or calls toStorageFrag if already at a storage root. */
class NavigateBackUseCase(private val nav: FileNavigatorRepository) {
    suspend fun navigateBack(toStorageFrag: () -> Unit) {
        val currentPath = nav.currentPath.value ?: return
        val parentPath = nav.getParentPath(currentPath)
        if (parentPath == null) {
            toStorageFrag()
        } else {
            nav.navigate(parentPath)
        }
    }
}
