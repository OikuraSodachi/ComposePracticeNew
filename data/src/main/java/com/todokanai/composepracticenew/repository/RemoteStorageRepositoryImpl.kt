package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.room.RemoteStorageInfo
import com.todokanai.composepracticenew.data.room.RemoteStorageInfoDao
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 원격 스토리지 접속 정보의 Room 기반 구현체. */
@Singleton
class RemoteStorageRepositoryImpl @Inject constructor(
    private val dao: RemoteStorageInfoDao
) {

    fun getAll(): Flow<List<RemoteStorageItem>> =
        dao.getAll().map { list -> list.map { it.toItem() } }

    suspend fun insert(item: RemoteStorageItem) =
        dao.insert(item.toEntity())

    suspend fun delete(item: RemoteStorageItem) =
        dao.delete(item.toEntity())
}

private fun RemoteStorageInfo.toItem() = RemoteStorageItem(
    id = id,
    name = name,
    address = address,
    port = port,
    userId = userId,
    password = password
)

private fun RemoteStorageItem.toEntity() = RemoteStorageInfo(
    id = id,
    name = name,
    address = address,
    port = port,
    userId = userId,
    password = password
)
