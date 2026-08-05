package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.LocalDataRepository
import kotlinx.coroutines.flow.Flow

/** 원격 스토리지 접속 정보의 조회·추가·수정·삭제를 담당하는 UseCase. */
class RemoteStorageUseCase(private val repo: LocalDataRepository) {
    fun getAll(): Flow<List<RemoteStorageItem>> = repo.getAll()
    suspend fun add(name: String, address: String, port: Int, userId: String, password: String) =
        repo.insert(RemoteStorageItem(name = name, address = address, port = port, userId = userId, password = password))
    suspend fun update(item: RemoteStorageItem) = repo.insert(item)
    suspend fun delete(item: RemoteStorageItem) = repo.delete(item)
}
