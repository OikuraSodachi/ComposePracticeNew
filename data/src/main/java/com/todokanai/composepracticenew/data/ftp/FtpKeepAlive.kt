package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException

/** 연결 성공 후 주기적으로 NOOP을 전송해 서버 idle timeout을 방지한다. */
internal class FtpKeepAlive(
    private val client: FTPClient,
    private val ioMutex: Mutex,
    private val isConnected: StateFlow<Boolean>,
    private val onDropped: suspend () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive && isConnected.value) {
                delay(KEEPALIVE_INTERVAL_MS)
                ioMutex.withLock {
                    runCatching { client.sendNoOp() }
                        .onFailure { e ->
                            if (e is FTPConnectionClosedException) onDropped()
                        }
                }
            }
        }
    }

    suspend fun stop() {
        job?.cancel()
        job?.join()
        job = null
    }

    companion object {
        private const val KEEPALIVE_INTERVAL_MS = 30_000L
    }
}
