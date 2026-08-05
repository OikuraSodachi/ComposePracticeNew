package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow

/** 사용자 정렬 설정 및 원격 스토리지 접속 정보의 영속성을 담당하는 통합 Repository 인터페이스. */
interface LocalDataRepository {
    val sortBy: Flow<String>
    fun saveSortBy(value: String)
    fun getAll(): Flow<List<RemoteStorageItem>>
    suspend fun insert(item: RemoteStorageItem)
    suspend fun delete(item: RemoteStorageItem)
}
