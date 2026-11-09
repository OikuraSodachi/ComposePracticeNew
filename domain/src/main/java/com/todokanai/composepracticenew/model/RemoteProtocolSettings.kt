package com.todokanai.composepracticenew.model

/** 원격 스토리지가 쓰는 프로토콜과 그 프로토콜에만 있는 접속 설정. 프로토콜을 추가하면 이 타입의 exhaustive `when`이 처리할 곳을 컴파일 오류로 알려 준다. */
sealed interface RemoteProtocolSettings {

    /**
     * FTP 접속 설정.
     * @param encoding 제어 연결의 문자 인코딩 (예: UTF-8, EUC-KR)
     */
    data class Ftp(val encoding: String) : RemoteProtocolSettings
}
