package com.todokanai.composepracticenew.data.ftp

import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * 쓰기 한 번이 [writeTimeoutMillis] 안에 끝나지 않으면 스스로 닫히는 소켓.
 * java.net.Socket에는 쓰기 제한 시간이 없어(SO_TIMEOUT은 읽기에만 적용된다) 받는 쪽이 읽기를 멈추면 write가 끝나지 않는다.
 * 제한 시간이 지나면 소켓을 중단으로 닫아([abort]) 막혀 있던 write는 [SocketTimeoutException]으로 끝나고, 소켓은 닫힌 채로 남는다.
 * 제한은 쓰기 한 번(버퍼 하나)을 기다리는 시간이며, 데이터가 계속 나가는 긴 전송은 끊지 않는다.
 * @param writeTimeoutMillis 쓰기 한 번이 끝나기를 기다리는 상한(ms). 0보다 커야 한다
 */
internal class WriteBoundedSocket(private val writeTimeoutMillis: Long) : Socket() {

    init {
        // 0 이하면 감시가 쓰기마다 곧바로 소켓을 닫아, 상한이 아니라 모든 쓰기의 실패가 된다
        require(writeTimeoutMillis > 0) { "writeTimeoutMillis must be positive: $writeTimeoutMillis" }
    }

    /** 쓰기가 제한 시간을 넘겨 이 소켓을 닫았는지. 감시 스레드가 쓰고 쓰기 스레드가 읽는다. */
    @Volatile
    private var writeTimedOut = false

    override fun getOutputStream(): OutputStream = BoundedOutput(super.getOutputStream())

    /**
     * 쓰기마다 제한 시간을 거는 출력 스트림. 실제로 소켓에 쓰는 가장 아래 스트림이므로, 위에서 감싼 버퍼 스트림의 쓰기도 모두 여기를 거친다.
     * @param out 소켓의 원래 출력 스트림
     */
    private inner class BoundedOutput(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = bounded { out.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) = bounded { out.write(b, off, len) }
        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    /**
     * [write]를 실행하는 동안 제한 시간을 건다. 시간 안에 끝나지 않으면 감시 스레드가 소켓을 닫아 [write]를 깨운다.
     * [write]가 돌아왔어도 제한 시간이 먼저 지났으면 실패다 — 소켓은 중단으로 닫혀 보낸 데이터가 버려진다.
     * @param write 소켓에 쓰는 동작
     * @throws SocketTimeoutException 제한 시간이 지나 소켓을 닫았거나 이미 그렇게 닫힌 경우
     */
    private inline fun bounded(write: () -> Unit) {
        val deadline = WriteDeadline(watchdog, writeTimeoutMillis) { closeForTimeout() }
        val failure = try {
            write()
            null
        } catch (e: IOException) {
            e
        } finally {
            deadline.finish()
        }
        if (deadline.expired || (failure != null && writeTimedOut)) {
            throw SocketTimeoutException(WRITE_TIMED_OUT_MESSAGE).apply { failure?.let(::initCause) }
        }
        if (failure != null) throw failure
    }

    /**
     * 진행 중인 전송을 중단하려고 소켓을 닫는다. 보내지 못한 데이터를 버리고 연결을 리셋(RST)한다.
     * 일반 [close]는 남은 데이터를 다 보낸 뒤 연결을 닫으므로, 데이터 연결에서는 받는 쪽에 파일 끝으로 읽힌다(RFC 959 §3.4.1) —
     * 리셋은 받는 쪽이 중단을 파일 끝과 구분하게 한다.
     */
    fun abort() {
        runCatching { setSoLinger(true, 0) }
        runCatching { close() }
    }

    /** 제한 시간을 넘긴 쓰기를 끝내려고 소켓을 중단으로 닫는다. 감시 스레드에서 실행된다. */
    private fun closeForTimeout() {
        writeTimedOut = true
        abort()
    }

    companion object {
        /** 쓰기 제한 시간이 지나 소켓을 닫았을 때의 예외 메시지. 읽기 제한 시간 초과(`Read timed out`)와 짝을 이룬다. */
        private const val WRITE_TIMED_OUT_MESSAGE = "Write timed out"

        /** 모든 소켓의 쓰기 제한 시간을 재는 감시 스레드. 앱 종료를 막지 않도록 데몬으로 두고, 취소한 예약은 바로 지운다. */
        private val watchdog = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "ftp-write-timeout").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
