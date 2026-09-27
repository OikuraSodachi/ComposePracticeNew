package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.RemoteStorageItem as DomainRemoteStorageItem
import com.todokanai.composepracticenew.myobjects.Constants

/**
 * 원격 스토리지 접속 정보를 표현하는 앱 레이어 모델. domain 모듈의 RemoteStorageItem을 앱 레이어에서 격리한다.
 * @param id 고유 식별자
 * @param name 스토리지 표시 이름
 * @param address 서버 주소
 * @param port 서버 포트 번호
 * @param userId 로그인 아이디
 * @param password 로그인 비밀번호
 * @param encoding 파일명 인코딩
 */
@Immutable
data class RemoteStorageItem(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val userId: String,
    val password: String,
    val encoding: String = Constants.FTP_ENCODING_DEFAULT
) {
    companion object {
        /** domain 모듈의 RemoteStorageItem을 앱 레이어 모델로 변환한다. */
        fun from(domain: DomainRemoteStorageItem) = RemoteStorageItem(
            id = domain.id,
            name = domain.name,
            address = domain.address,
            port = domain.port,
            userId = domain.userId,
            password = domain.password,
            encoding = domain.encoding
        )
    }

    /** UseCase 호출 시 domain 모델로 역변환한다. */
    fun toDomain() = DomainRemoteStorageItem(
        id = id,
        name = name,
        address = address,
        port = port,
        userId = userId,
        password = password,
        encoding = encoding
    )
}
