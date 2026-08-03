package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow

/** 원격 스토리지 접속 정보의 영속성을 담당하는 Repository 인터페이스. */
interface RemoteStorageRepository {
    fun getAll(): Flow<List<RemoteStorageItem>>
    suspend fun insert(item: RemoteStorageItem)
    suspend fun delete(item: RemoteStorageItem)
}
