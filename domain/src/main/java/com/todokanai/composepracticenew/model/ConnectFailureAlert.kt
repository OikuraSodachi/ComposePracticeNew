package com.todokanai.composepracticenew.model

/** 화면에 잠깐 알릴 원격 연결 실패. 다시 시도하면 될 수 있는 실패만 해당한다 — 서버나 포트를 쓸 수 없는 실패는 화면이 닫을 때까지 남는 다이얼로그로 알린다. */
enum class ConnectFailureAlert {
    /** 서버에 접속하지 못했거나 로그인이 거부됐다. */
    CONNECTION_FAILED
}
