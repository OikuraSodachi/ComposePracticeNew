package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File

/** Deletes the given files and refreshes the current directory on completion. */
class DeleteFilesUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(files: Array<File>) = fileAction.deleteAction(files)
}
