package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileNavigatorRepository

/** Navigates to the parent directory, or calls toStorageFrag if already at a storage root. */
class NavigateBackUseCase(private val nav: FileNavigatorRepository) {
    suspend operator fun invoke(toStorageFrag: () -> Unit) {
        val parentFile = nav.currentPath.value.parentFile
        if (parentFile?.listFiles() == null) {
            toStorageFrag()
        } else {
            nav.setCurrentPath(parentFile)
        }
    }
}
