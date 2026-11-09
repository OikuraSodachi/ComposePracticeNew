package com.todokanai.composepracticenew.model

/**
 * 원격 스토리지 접속 정보를 표현하는 도메인 모델.
 * @param id 고유 식별자
 * @param name 스토리지 표시 이름
 * @param address 서버 주소
 * @param port 서버 포트 번호
 * @param userId 로그인 아이디
 * @param password 로그인 비밀번호
 * @param settings 프로토콜과 그 프로토콜의 접속 설정
 */
data class RemoteStorageItem(
    val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val userId: String,
    val password: String,
    val settings: RemoteProtocolSettings
) {
    companion object {
        /** TCP 포트로 허용하는 범위. 0은 예약 포트라 제외한다. 입력 검증과 연결 전 검사가 같은 범위를 쓴다. */
        val PORT_RANGE = 1..65535
    }
}
