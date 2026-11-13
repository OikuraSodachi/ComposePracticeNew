package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.StorageVolumeInfo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 정해진 경로들을 저장소 볼륨 루트로 알리는 테스트용 StorageVolumeRepository.
 * @param roots 볼륨 루트로 알릴 경로들
 */
class FakeStorageVolumes(vararg roots: String) : StorageVolumeRepository {
    /** [roots]를 공간 정보 없이 담은 볼륨 목록. */
    override val storageList = MutableStateFlow(roots.map { StorageVolumeInfo(it, 0L, 0L) })
    override fun load() {}
}
