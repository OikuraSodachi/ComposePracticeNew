package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTPConnectionClosedException
import javax.inject.Inject

/**
 * 연결 성공 후 주기적으로 NOOP을 전송해 서버 idle timeout을 방지한다.
 * @param session NOOP 전송과 연결 끊김 처리를 위임할 FTP 세션
 */
class FtpKeepAlive @Inject constructor(
    private val session: FtpSession
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** NOOP 전송 루프를 (재)시작한다. */
    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive && session.isConnected.value) {
                delay(KEEPALIVE_INTERVAL_MS)
                session.withClient { client ->
                    runCatching { client.sendNoOp() }
                        .onFailure { e ->
                            if (e is FTPConnectionClosedException) session.markConnectionDropped()
                        }
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
