package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * 물리 스토리지 목록을 조회해 표시 모델로 변환하고 StorageRepository에 저장하는 UseCase.
 * @param storageRepo 스토리지 목록 조회·저장을 담당하는 StorageVolumeRepository 구현체
 */
class GetStorageListUseCase(private val storageRepo: StorageVolumeRepository) {
    /**
     * 물리 스토리지 목록을 조회해 표시 모델로 변환하고 저장한다.
     * @return Unit
     */
    fun execute() {
        val list = storageRepo.loadPhysicalStorages().map { info ->
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
