package com.todokanai.composepracticenew.model

/**
 * 원격 서버와의 연결 상태.
 * 예기치 않은 끊김은 사건이 아니라 [Lost] 상태로 남는다 — 끊긴 뒤에 관찰을 시작한 쪽도 그 사실을 알 수 있도록.
 * 연결된 상태는 어느 서버에 연결됐는지를 함께 담는다 — 연결 여부와 연결한 서버가 따로 바뀌어 서로 어긋나지 않도록.
 */
sealed interface RemoteConnectionState {
    /** 연결되지 않았거나 앱이 연결을 종료했다. 다른 서버로 다시 연결하는 중도 여기에 해당한다. */
    data object Disconnected : RemoteConnectionState

    /**
     * 서버 연결과 로그인이 완료됐다.
     * @param serverName 연결한 서버의 표시 이름(연결할 때 쓴 접속 정보의 이름)
     */
    data class Connected(val serverName: String) : RemoteConnectionState

    /** 연결된 세션이 사용자의 요청 없이 끝났다(서버 종료·응답 없음·시스템의 백그라운드 실행 한도 등). 다음 연결 시도나 종료 요청까지 유지된다. */
    data object Lost : RemoteConnectionState
}
