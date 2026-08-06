package com.todokanai.composepracticenew.ui.model

import com.todokanai.composepracticenew.model.RemoteStorageItem as DomainRemoteStorageItem

/** 원격 스토리지 접속 정보를 표현하는 앱 레이어 모델. domain 모듈의 RemoteStorageItem을 앱 레이어에서 격리한다. */
data class RemoteStorageItem(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val userId: String,
    val password: String
) {
    companion object {
        /** domain 모듈의 RemoteStorageItem을 앱 레이어 모델로 변환한다. */
        fun from(domain: DomainRemoteStorageItem) = RemoteStorageItem(
            id = domain.id,
            name = domain.name,
            address = domain.address,
            port = domain.port,
            userId = domain.userId,
            password = domain.password
        )
    }

    /** UseCase 호출 시 domain 모델로 역변환한다. */
    fun toDomain() = DomainRemoteStorageItem(
        id = id,
        name = name,
        address = address,
        port = port,
        userId = userId,
        password = password
    )
}
