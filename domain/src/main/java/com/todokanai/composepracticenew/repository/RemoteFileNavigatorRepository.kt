package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteStorageItem

/**
 * 원격 스토리지 연결과 원격 경로 이동을 지원하는 FileNavigatorRepository 확장 인터페이스.
 * 로컬 전용 구현체는 이 인터페이스를 구현할 필요가 없다.
 */
interface RemoteFileNavigatorRepository : FileNavigatorRepository {
    /**
     * 원격 스토리지 서버에 연결하고 로그인한다.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 성공이면 true, 실패이면 false
     */
    suspend fun connectRemote(item: RemoteStorageItem): Boolean

    /**
     * [item] 서버의 루트 디렉터리로 탐색 위치를 이동시킨다. 루트 경로 형식은 연결 구현체가 결정한다.
     * @param item 이동할 원격 스토리지 접속 정보
     */
    suspend fun navigateToRemoteRoot(item: RemoteStorageItem)
}
