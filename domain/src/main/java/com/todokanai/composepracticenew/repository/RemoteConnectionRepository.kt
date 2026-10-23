package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.StateFlow

/** 원격 스토리지 서버와의 연결 수명주기와 연결 상태를 다루는 프로토콜 중립 계약. */
interface RemoteConnectionRepository {

    /** 연결 또는 연결 해제가 진행 중이면 true를 방출한다. */
    val isConnecting: StateFlow<Boolean>

    /** 서버 연결 및 로그인이 완료된 상태이면 true를 방출한다. */
    val isConnected: StateFlow<Boolean>

    /**
     * [item]의 접속 정보로 서버에 연결하고 로그인한다.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 결과. 로그인했어도 서버를 탐색할 수 없으면 연결을 종료하고 실패를 돌려준다
     */
    suspend fun connect(item: RemoteStorageItem): ConnectResult

    /** 서버와의 연결을 종료한다. */
    suspend fun disconnect()

    /**
     * [item] 서버의 루트 디렉터리를 가리키는 경로(스킴 포함)를 반환한다.
     * @param item 원격 스토리지 접속 정보
     * @return 탐색 가능한 루트 경로 문자열
     */
    fun rootPathOf(item: RemoteStorageItem): String
}
