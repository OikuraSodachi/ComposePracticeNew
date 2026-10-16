package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.Flow

/** 원격 스토리지 접속 정보의 저장·조회 계약. */
interface RemoteStorageRepository {
    /** 저장된 접속 정보 목록을 방출한다. */
    fun getAll(): Flow<List<RemoteStorageItem>>

    /** [item]을 저장한다. 같은 id가 있으면 교체한다. */
    suspend fun insert(item: RemoteStorageItem)

    /** [item]을 삭제한다. */
    suspend fun delete(item: RemoteStorageItem)

    /** [id]에 해당하는 접속 정보를 반환한다. 없으면 null. */
    suspend fun getById(id: Long): RemoteStorageItem?
}
