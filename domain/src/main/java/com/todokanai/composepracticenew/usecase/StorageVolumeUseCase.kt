package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 로컬 스토리지 볼륨 목록 상태를 노출하는 UseCase.
 * @param repo 스토리지 볼륨 목록 상태를 제공하는 StorageVolumeRepository 구현체
 */
class StorageVolumeUseCase(private val repo: StorageVolumeRepository) {
    val storageList: StateFlow<List<StorageHolderItem>> = repo.storageList
}
