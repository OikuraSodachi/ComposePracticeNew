package com.todokanai.composepracticenew.repository

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton in-memory store for the physical storage device list (raw space values; display formatting belongs to the app layer).
 * @param context StorageManager 조회에 사용하는 애플리케이션 Context
 */
@Singleton
class StorageVolumeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageVolumeRepository {
    private val _storageList = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    override val storageList: StateFlow<List<StorageVolumeInfo>> get() = _storageList

    override fun load() {
        _storageList.value = scanPhysicalVolumes()
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
