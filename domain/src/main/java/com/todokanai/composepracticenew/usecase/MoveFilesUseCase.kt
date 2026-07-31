package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File

/** Moves the given files into the specified destination directory. */
class MoveFilesUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(files: Array<File>, currentPath: File) = fileAction.moveAction(files, currentPath)
}
