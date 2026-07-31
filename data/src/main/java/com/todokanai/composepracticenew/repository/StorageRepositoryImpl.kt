package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.StorageHolderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Singleton in-memory store for the physical storage device list. */
@Singleton
class StorageRepositoryImpl @Inject constructor() : StorageRepository {
    private val _storageList = MutableStateFlow<List<StorageHolderItem>>(emptyList())
    override val storageList: StateFlow<List<StorageHolderItem>> get() = _storageList

    override fun setStorageList(list: List<StorageHolderItem>) {
        _storageList.value = list
    }
}
