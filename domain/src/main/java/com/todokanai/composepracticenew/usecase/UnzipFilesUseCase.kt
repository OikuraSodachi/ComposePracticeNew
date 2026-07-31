package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File
import java.util.zip.ZipFile

/** Extracts a zip archive into the specified directory. */
class UnzipFilesUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(zipFile: ZipFile, currentPath: File, unzipHere: Boolean) =
        fileAction.unzipAction(zipFile, currentPath, unzipHere)
}
