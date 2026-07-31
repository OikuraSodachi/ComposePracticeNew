package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import java.io.File

/** Navigates the file browser to the given directory and refreshes the file list. */
class NavigateToDirectoryUseCase(private val nav: FileNavigatorRepository) {
    suspend operator fun invoke(file: File) = nav.setCurrentPath(file)
}
