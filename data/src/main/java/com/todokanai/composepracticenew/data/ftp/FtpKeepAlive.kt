package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 연결 성공 후 주기적으로 NOOP을 전송해 서버 idle timeout을 방지한다.
 * 세션과 수명을 맞추기 위해 @Singleton — 어디서 주입하든 같은 NOOP 루프를 시작·중단한다.
 * @param session NOOP 전송과 연결 끊김 처리를 위임할 FTP 세션
 */
@Singleton
class FtpKeepAlive @Inject constructor(
    private val session: FtpSession
) {
    /** NOOP 루프를 실행하는 세션 독립 코루틴 스코프. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 실행 중인 NOOP 루프 Job. 중단 상태이면 null. */
    private var job: Job? = null

    /** NOOP 전송 루프를 (재)시작한다. */
    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive && session.isConnected.value) {
                delay(KEEPALIVE_INTERVAL_MS)
                // NOOP 응답을 읽지 못하면(끊김·타임아웃) 세션이 응답 순서를 잃으므로 withConnectedClient가 세션을 종료하고,
                // 끊김 상태가 되어 다음 루프 조건(isConnected)에서 종료된다. NOOP을 거부한 응답은 세션 유지에 영향이 없어 결과를 보지 않는다
                session.withConnectedClient { client ->
                    client.sendNoOp()
                    IoResult.Done
                }
            }
        }
    }

    /** NOOP 전송 루프를 중단하고 종료를 기다린다. */
    suspend fun stop() {
        job?.cancel()
        job?.join()
        job = null
    }

    companion object {
        private const val KEEPALIVE_INTERVAL_MS = 30_000L
    }
}
