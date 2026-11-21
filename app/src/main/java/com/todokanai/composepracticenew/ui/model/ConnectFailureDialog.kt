package com.todokanai.composepracticenew.ui.model

/** 저장소 화면이 사용자가 닫을 때까지 띄우는 연결 실패 다이얼로그의 종류. */
enum class ConnectFailureDialog {
    /** 서버의 파일 목록을 읽을 수 없어 연결을 종료했다. */
    UNREADABLE_SERVER,

    /** 서버가 바이너리 전송 방식(TYPE I)을 거부해 연결을 종료했다. */
    BINARY_TYPE_REFUSED,

    /** 저장된 포트가 범위를 벗어나 연결을 시도하지 않았다. */
    INVALID_PORT,
}
