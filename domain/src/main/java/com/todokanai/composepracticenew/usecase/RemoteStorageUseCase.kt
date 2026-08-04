package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteStorageRepository
import kotlinx.coroutines.flow.Flow

/** 원격 스토리지 접속 정보의 조회 및 추가를 담당하는 UseCase. */
class RemoteStorageUseCase(private val repo: RemoteStorageRepository) {
    fun getAll(): Flow<List<RemoteStorageItem>> = repo.getAll()
    suspend fun add(name: String, address: String, port: Long, userId: String, password: String) =
        repo.insert(RemoteStorageItem(name = name, address = address, port = port, userId = userId, password = password))
}
