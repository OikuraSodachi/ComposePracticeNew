package com.todokanai.composepracticenew.model

/**
 * 사용자가 입력한 원격 스토리지 접속 정보. 검증 전의 값이라 포트는 입력한 문자열 그대로다.
 * @param id 수정할 항목의 id. 새 항목이면 0
 * @param name 사용자가 지정한 서버 별칭
 * @param address 서버 주소 (IP 또는 호스트명)
 * @param port 사용자가 입력한 포트 문자열
 * @param userId 로그인 아이디
 * @param password 로그인 비밀번호
 * @param settings 프로토콜과 그 프로토콜의 접속 설정
 */
data class RemoteStorageInput(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: String,
    val userId: String,
    val password: String,
    val settings: RemoteProtocolSettings
)
