package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.RemoteStorageItem

/**
 * 연결한 원격 서버의 루트 디렉터리로 원격 화면의 탐색 위치를 옮기는 계약.
 * 루트 경로를 만드는 일과 그 경로로 이동하는 일을 한 구현에 두어, 경로 형식이 탐색 저장소와 어긋날 수 없게 한다(#445).
 * 이동을 거부하는 결과가 없다 — 루트에 접근할 권한이 없으면 이동은 그대로 하고 목록 조회 실패로 드러내, 연결은 유지한 채 다시 시도할 수 있게 한다.
 */
interface RemoteRootNavigator {

    /**
     * [item] 서버의 루트 디렉터리로 원격 화면의 탐색 위치를 옮긴다.
     * @param item 연결한 원격 스토리지 접속 정보
     */
    suspend fun navigateToRoot(item: RemoteStorageItem)
}
