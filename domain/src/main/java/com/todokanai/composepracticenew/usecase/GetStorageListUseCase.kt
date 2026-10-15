package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.StorageVolumeRepository

/**
 * 스토리지 볼륨 목록을 로드해 UI 상태를 갱신하는 UseCase.
 * @param storageRepo 스토리지 볼륨 목록 로드·저장을 담당하는 StorageVolumeRepository 구현체
 */
class GetStorageListUseCase(private val storageRepo: StorageVolumeRepository) {
    /**
     * 스토리지 볼륨 목록을 소스에서 다시 로드해 갱신한다.
     * @return Unit
     */
    fun execute() = storageRepo.load()
}
