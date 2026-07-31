package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File

/** Renames the given file to the specified name. */
class RenameFileUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(file: File, name: String) = fileAction.renameAction(file, name)
}
