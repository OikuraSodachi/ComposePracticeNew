package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.FileNavigatorRepository

/** 원격 스토리지에 접속하여 파일 탐색기를 해당 스토리지 루트로 이동시킨다. */
class ConnectRemoteStorageUseCase(private val navigator: FileNavigatorRepository) {
    suspend operator fun invoke(item: RemoteStorageItem) {
        navigator.navigateToRemote(item)
    }
}
