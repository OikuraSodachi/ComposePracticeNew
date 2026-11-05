package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.StateFlow

/** 원격 스토리지 서버와의 연결 수명주기와 연결 상태를 다루는 프로토콜 중립 계약. */
interface RemoteConnectionRepository {

    /** 연결 또는 연결 해제가 진행 중이면 true를 방출한다. */
    val isConnecting: StateFlow<Boolean>

    /** 서버와의 연결 상태. 예기치 않은 끊김은 다음 연결 시도나 종료 요청까지 [RemoteConnectionState.Lost]로 남는다. */
    val connectionState: StateFlow<RemoteConnectionState>

    /**
     * [item]의 접속 정보로 서버에 연결하고 로그인한다.
     * @param item 연결할 원격 스토리지 접속 정보
     * @return 연결 결과. 로그인했어도 서버를 탐색할 수 없으면 연결을 종료하고 실패를 돌려준다
     */
    suspend fun connect(item: RemoteStorageItem): ConnectResult

    /** 서버와의 연결을 종료한다. */
    suspend fun disconnect()

    /**
     * 앱이 연결을 더 유지할 수 없어(시스템의 백그라운드 실행 한도 등) 연결된 세션을 종료한다.
     * 사용자가 요청한 종료가 아니므로 [RemoteConnectionState.Lost]로 남는다 — 끊긴 세션에 기대는 상태를 버리도록.
     * 연결된 세션이 없으면(연결 작업 중·이미 끊김·종료됨) 아무 동작도 하지 않는다.
     * @return 연결된 세션을 끝냈으면 true
     */
    suspend fun drop(): Boolean

    /**
     * [item] 서버의 루트 디렉터리를 가리키는 경로(스킴 포함)를 반환한다.
     * @param item 원격 스토리지 접속 정보
     * @return 탐색 가능한 루트 경로 문자열. 원격 탐색 저장소가 다루는 경로 형식이어야 한다(어긋나면 배선 오류)
     */
    fun rootPathOf(item: RemoteStorageItem): String
}
