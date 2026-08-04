package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import java.io.File

/** Opens a file with an appropriate external app, or navigates into it if it is a directory. */
class OpenFileUseCase(
    private val nav: FileNavigatorRepository,
    private val openFile: (File) -> Unit
) {
    suspend fun open(file: File) {
        if (file.isDirectory) nav.setCurrentPath(file.absolutePath)
        else openFile(file)
    }
}
