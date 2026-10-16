package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 로컬 스토리지 볼륨 목록 상태를 노출하는 UseCase.
 * @param repo 스토리지 볼륨 목록 상태를 제공하는 StorageVolumeRepository 구현체
 */
class StorageVolumeUseCase(private val repo: StorageVolumeRepository) {
    /** 스토리지 볼륨의 원시 공간 정보 목록. */
    val storageList: StateFlow<List<StorageVolumeInfo>> = repo.storageList
}
