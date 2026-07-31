package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.FileActionRepository
import java.io.File

/** Creates a new folder with the given name inside the specified directory. */
class NewFolderUseCase(private val fileAction: FileActionRepository) {
    operator fun invoke(currentPath: File, folderName: String) = fileAction.newFolderAction(currentPath, folderName)
}
