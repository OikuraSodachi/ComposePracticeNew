package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.StorageVolumeInfo
import kotlinx.coroutines.flow.StateFlow

/** Contract for reading and updating the physical storage list. */
interface StorageVolumeRepository {
    /** 현재 스토리지 볼륨 목록 상태(원시 공간 정보). */
    val storageList: StateFlow<List<StorageVolumeInfo>>
    /**
     * 스토리지 볼륨 목록을 소스에서 다시 로드해 [storageList]를 갱신한다.
     * @return Unit
     */
    fun load()
}
