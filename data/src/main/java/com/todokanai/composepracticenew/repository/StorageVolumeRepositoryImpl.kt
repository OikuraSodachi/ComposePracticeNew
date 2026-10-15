package com.todokanai.composepracticenew.repository

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Singleton in-memory store for the physical storage device list. */
@Singleton
class StorageVolumeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageVolumeRepository {
    private val _storageList = MutableStateFlow<List<StorageHolderItem>>(emptyList())
    override val storageList: StateFlow<List<StorageHolderItem>> get() = _storageList

    override fun load() {
        _storageList.value = scanPhysicalVolumes().map { info ->
            val progress = if (info.totalSpace == 0L) 0f
            else ((info.totalSpace - info.freeSpace).toDouble() / info.totalSpace.toDouble()).toFloat()
            StorageHolderItem(
                absolutePath = info.path,
                used = readableFileSize_td(info.totalSpace - info.freeSpace),
                total = readableFileSize_td(info.totalSpace),
                progress = progress
            )
        }
    }

    private fun scanPhysicalVolumes(): List<StorageVolumeInfo> {
        val defaultStorage = Environment.getExternalStorageDirectory()
        val volumes = context.getSystemService(StorageManager::class.java)?.storageVolumes
        val result = mutableListOf(
            StorageVolumeInfo(defaultStorage.absolutePath, defaultStorage.totalSpace, defaultStorage.freeSpace)
        )
        volumes?.forEach { volume ->
            if (!volume.isPrimary && volume.isRemovable) {
                val sdCard = volume.directory ?: return@forEach
                result.add(StorageVolumeInfo(sdCard.absolutePath, sdCard.totalSpace, sdCard.freeSpace))
            }
        }
        return result
    }
}
