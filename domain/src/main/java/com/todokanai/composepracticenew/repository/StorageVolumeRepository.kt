package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import kotlinx.coroutines.flow.StateFlow

/** Contract for reading and updating the physical storage list. */
interface StorageVolumeRepository {
    val storageList: StateFlow<List<StorageHolderItem>>
    fun setStorageList(list: List<StorageHolderItem>)
    fun loadPhysicalStorages(): List<StorageVolumeInfo>
}
