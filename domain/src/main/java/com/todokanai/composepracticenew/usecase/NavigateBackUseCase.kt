package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import java.io.File

/** Navigates to the parent directory, or calls toStorageFrag if already at a storage root. */
class NavigateBackUseCase(private val nav: FileNavigatorRepository) {
    suspend fun navigateBack(toStorageFrag: () -> Unit) {
        val parentFile = nav.currentPath.value?.let { File(it).parentFile }
        if (parentFile?.listFiles() == null) {
            toStorageFrag()
        } else {
            nav.setLocalPath(parentFile.absolutePath)
        }
    }
}
