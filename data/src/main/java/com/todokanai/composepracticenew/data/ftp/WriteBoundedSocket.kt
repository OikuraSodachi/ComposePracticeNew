package com.todokanai.composepracticenew.data.ftp

import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 쓰기 한 번이 [writeTimeoutMillis] 안에 끝나지 않으면 스스로 닫히는 소켓.
 * java.net.Socket에는 쓰기 제한 시간이 없어(SO_TIMEOUT은 읽기에만 적용된다) 받는 쪽이 읽기를 멈추면 write가 끝나지 않는다.
 * 제한 시간이 지나 닫히면 막혀 있던 write는 [SocketTimeoutException]으로 끝나고, 소켓은 닫힌 채로 남는다.
 * 제한은 쓰기 한 번(버퍼 하나)을 기다리는 시간이며, 데이터가 계속 나가는 긴 전송은 끊지 않는다.
 * @param writeTimeoutMillis 쓰기 한 번이 끝나기를 기다리는 상한(ms)
 */
internal class WriteBoundedSocket(private val writeTimeoutMillis: Long) : Socket() {

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
     * @param write 소켓에 쓰는 동작
     * @throws SocketTimeoutException 제한 시간이 지나 소켓을 닫았거나 이미 그렇게 닫힌 경우
     */
    private inline fun bounded(write: () -> Unit) {
        val timeout = watchdog.schedule(Runnable { closeForTimeout() }, writeTimeoutMillis, TimeUnit.MILLISECONDS)
        try {
            write()
        } catch (e: IOException) {
            if (writeTimedOut) throw SocketTimeoutException(WRITE_TIMED_OUT_MESSAGE).apply { initCause(e) }
            throw e
        } finally {
            timeout.cancel(false)
        }
    }

    /** 제한 시간을 넘긴 쓰기를 끝내려고 소켓을 닫는다. 감시 스레드에서 실행된다. */
    private fun closeForTimeout() {
        writeTimedOut = true
        runCatching { close() }
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
