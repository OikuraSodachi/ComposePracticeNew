package com.todokanai.composepracticenew.model

/** 화면에 잠깐 알릴 원격 연결 실패. 접속 정보·네트워크를 확인하고 다시 시도할 실패만 해당한다 — 서버나 포트를 쓸 수 없는 실패는 화면이 닫을 때까지 남는 다이얼로그로 알린다. */
enum class ConnectFailureAlert {
    /** 아래로 나눌 수 없는 연결 실패. */
    CONNECTION_FAILED,

    /** 서버가 제한 시간 안에 응답하지 않았다. */
    TIMED_OUT,

    /** 서버 주소의 호스트를 찾지 못했다. */
    HOST_NOT_FOUND,

    /** 서버에 접속할 수 없다. */
    UNREACHABLE,

    /** 서버가 로그인을 거부했다. */
    LOGIN_REFUSED,
}
