package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/** Converts raw storage volume info into display models and updates StorageRepository. */
class GetStorageListUseCase(private val storageRepo: StorageVolumeRepository) {
    fun execute(storages: List<StorageVolumeInfo>) {
        val list = storages.map { info ->
            val progress = if (info.totalSpace == 0L) 0f
                else ((info.totalSpace - info.freeSpace).toDouble() / info.totalSpace.toDouble()).toFloat()
            StorageHolderItem(
                absolutePath = info.path,
                used = readableFileSize_td(info.totalSpace - info.freeSpace),
                total = readableFileSize_td(info.totalSpace),
                progress = progress
            )
        }
        storageRepo.setStorageList(list)
    }
}
