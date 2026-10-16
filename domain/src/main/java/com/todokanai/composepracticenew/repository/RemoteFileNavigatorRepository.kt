package com.todokanai.composepracticenew.repository

/**
 * 원격 스토리지 연결과 원격 경로 이동을 지원하는 FileNavigatorRepository 확장 인터페이스.
 * 로컬 전용 구현체는 이 인터페이스를 구현할 필요가 없다.
 */
interface RemoteFileNavigatorRepository : FileNavigatorRepository {
    /**
     * 원격 스토리지 서버에 연결하고 로그인한다.
     * @param address 서버 주소
     * @param port 포트 번호
     * @param userId 사용자 ID
     * @param password 비밀번호
     * @param encoding 문자 인코딩
     * @return 연결 성공이면 true, 실패이면 false
     */
    suspend fun connectRemote(address: String, port: Int, userId: String, password: String, encoding: String): Boolean

    /**
     * 지정된 원격 경로로 탐색 위치를 이동시킨다.
     * @param path 이동할 원격 경로
     */
    suspend fun setRemotePath(path: String)
}
