package com.todokanai.composepracticenew.model

/**
 * 원격 서버와의 연결 상태.
 * 예기치 않은 끊김은 사건이 아니라 [LOST] 상태로 남는다 — 끊긴 뒤에 관찰을 시작한 쪽도 그 사실을 알 수 있도록.
 */
enum class RemoteConnectionState {
    /** 연결되지 않았거나 앱이 연결을 종료했다. 다른 서버로 다시 연결하는 중도 여기에 해당한다. */
    DISCONNECTED,

    /** 서버 연결과 로그인이 완료됐다. */
    CONNECTED,

    /** 연결된 세션이 앱의 요청 없이 끊겼다(서버 종료·응답 없음 등). 다음 연결 시도나 종료 요청까지 유지된다. */
    LOST
}
