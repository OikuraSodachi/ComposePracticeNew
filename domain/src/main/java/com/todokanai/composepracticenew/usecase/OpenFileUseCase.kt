package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository

/** Opens a file with an appropriate external app, or navigates into it if it is a directory. */
class OpenFileUseCase(
    private val nav: FileNavigatorRepository,
    private val openFile: (String) -> Unit
) {
    suspend fun open(item: FileHolderItem) {
        if (item.isDirectory) nav.navigate(item.path)
        else openFile(item.path)
    }
}
