package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.repository.StorageRepository
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import java.io.File

/** Converts raw storage File list into display models and updates StorageRepository. */
class GetStorageListUseCase(private val storageRepo: StorageRepository) {
    operator fun invoke(storages: List<File>) {
        val list = storages.map { file ->
            val storageSize = file.totalSpace
            val freeSize = file.freeSpace
            val progress = ((storageSize.toDouble() - freeSize.toDouble()) / storageSize.toDouble()).toFloat()
            StorageHolderItem(
                storage = file,
                absolutePath = file.absolutePath,
                used = readableFileSize_td(storageSize - freeSize),
                total = readableFileSize_td(storageSize),
                progress = progress
            )
        }
        storageRepo.setStorageList(list)
    }
}
