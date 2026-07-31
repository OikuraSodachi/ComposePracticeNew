package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File

/** Compresses the given files into a zip archive with the specified name in the current directory. */
class ZipFilesUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(files: Array<File>, zipFileName: String) = fileAction.zipAction(files, zipFileName)
}
