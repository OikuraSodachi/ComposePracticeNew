package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File

/** Copies the given files into the specified destination directory. */
class CopyFilesUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(files: Array<File>, currentPath: File) = fileAction.copyAction(files, currentPath)
}
