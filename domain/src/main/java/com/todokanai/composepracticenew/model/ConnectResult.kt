package com.todokanai.composepracticenew.model

/** 원격 서버 연결 시도의 결과. 실패하면 연결은 남지 않는다. */
sealed interface ConnectResult {

    /** 연결과 로그인을 마쳐 서버를 탐색할 수 있다. */
    data object Connected : ConnectResult

    /**
     * 연결하지 못했거나, 연결했지만 쓸 수 없어 종료했다.
     * @param reason 실패한 이유
     */
    data class Failed(val reason: ConnectFailureReason) : ConnectResult
}

/** 원격 서버 연결이 실패한 이유. 이유마다 사용자에게 알리는 방식이 다르다. */
enum class ConnectFailureReason {
    /** 서버에 접속하지 못했거나 로그인이 거부됐다. 다시 시도하면 될 수 있다. */
    CONNECTION_FAILED,

    /** 접속과 로그인은 됐지만 서버가 파일 목록 형식을 알려주지 않거나 해석할 수 없는 형식이어서, 어느 폴더도 볼 수 없어 연결을 종료했다. */
    LISTING_FORMAT_UNKNOWN,
}

/** 마지막으로 접속한 서버에 자동으로 다시 연결한 결과. */
sealed interface ReconnectResult {

    /**
     * 다시 연결했다.
     * @param item 연결한 서버의 접속 정보
     */
    data class Connected(val item: RemoteStorageItem) : ReconnectResult

    /** 다시 연결할 서버가 저장돼 있지 않다. */
    data object NoSavedServer : ReconnectResult

    /**
     * 저장된 서버에 연결하지 못했다.
     * @param reason 실패한 이유
     */
    data class Failed(val reason: ConnectFailureReason) : ReconnectResult
}
