package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.RemoteProtocolSettings as DomainRemoteProtocolSettings

/** 원격 스토리지의 프로토콜과 그 프로토콜의 접속 설정을 표현하는 앱 레이어 모델. domain 모듈의 RemoteProtocolSettings를 앱 레이어에서 격리한다. */
@Immutable
sealed interface RemoteProtocolSettings {

    /** UseCase 호출 시 domain 모델로 역변환한다. */
    fun toDomain(): DomainRemoteProtocolSettings

    /**
     * FTP 접속 설정.
     * @param encoding 제어 연결의 문자 인코딩
     */
    data class Ftp(val encoding: String) : RemoteProtocolSettings {
        override fun toDomain() = DomainRemoteProtocolSettings.Ftp(encoding)

        companion object {
            /** 새 FTP 서버를 추가할 때 처음 채워 두는 포트. */
            const val DEFAULT_PORT = 21
        }
    }

    companion object {
        /** domain 모듈의 RemoteProtocolSettings를 앱 레이어 모델로 변환한다. */
        fun from(domain: DomainRemoteProtocolSettings): RemoteProtocolSettings = when (domain) {
            is DomainRemoteProtocolSettings.Ftp -> Ftp(domain.encoding)
        }
    }
}
