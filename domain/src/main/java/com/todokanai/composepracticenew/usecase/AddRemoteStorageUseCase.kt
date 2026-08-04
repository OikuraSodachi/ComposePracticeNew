package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.RemoteStorageRepository

/** 원격 스토리지 접속 정보를 Repository에 저장한다. */
class AddRemoteStorageUseCase(private val repo: RemoteStorageRepository) {
    suspend operator fun invoke(name: String, address: String, port: Long, userId: String, password: String) {
        repo.insert(RemoteStorageItem(name = name, address = address, port = port, userId = userId, password = password))
    }
}
