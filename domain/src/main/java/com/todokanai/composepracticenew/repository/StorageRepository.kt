package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.StorageHolderItem
import kotlinx.coroutines.flow.StateFlow

/** Contract for reading and updating the physical storage list. */
interface StorageRepository {
    val storageList: StateFlow<List<StorageHolderItem>>
    fun setStorageList(list: List<StorageHolderItem>)
}
