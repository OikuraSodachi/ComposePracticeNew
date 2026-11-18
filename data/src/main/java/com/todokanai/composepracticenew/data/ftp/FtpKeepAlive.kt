package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.delay

/**
 * 세션이 로그인돼 있는 동안 [intervalMillis]마다 NOOP을 보내 서버의 유휴 제한으로 끊기지 않게 한다. 세션마다 따로 돌며, 부른 쪽이 취소하거나 세션이 끊기면 끝난다.
 * 작업 세션에서는 다운로드가 같은 이름 질문의 답을 기다리는 동안(ioMutex를 쥐지 않는다)에도 연결을 유지한다.
 * 명령이 ioMutex를 쥐고 있으면 그 명령이 끝난 뒤에 보낸다.
 * @param intervalMillis NOOP을 보내는 간격(ms)
 */
internal suspend fun FtpSession.keepAlive(intervalMillis: Long) {
    while (isReady()) {
        delay(intervalMillis)
        // NOOP 응답을 읽지 못하면(끊김·타임아웃) 세션이 응답 순서를 잃으므로 withConnectedClient가 세션을 종료하고,
        // 로그인 상태가 풀려 다음 루프 조건(isReady)에서 끝난다. NOOP을 거부한 응답은 세션 유지에 영향이 없어 결과를 보지 않는다
        withConnectedClient { client ->
            client.sendNoOp()
            IoResult.Done
        }
    }
}

/** NOOP을 보내는 기본 간격(ms). */
internal const val KEEPALIVE_INTERVAL_MS = 30_000L
