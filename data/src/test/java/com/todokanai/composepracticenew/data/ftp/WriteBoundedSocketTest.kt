package com.todokanai.composepracticenew.data.ftp

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/** [WriteBoundedSocket]이 받는 쪽이 읽기를 멈춘 쓰기만 제한 시간에 끝내고, 데이터가 나가는 쓰기는 끊지 않는지 검증하는 단위 테스트. */
class WriteBoundedSocketTest {

    /** 테스트의 연결을 받는 로컬 서버 소켓. 받는 쪽 버퍼를 작게 해 읽지 않으면 쓰기가 곧 막히게 한다. */
    private val server = ServerSocket().apply {
        receiveBufferSize = SMALL_BUFFER
        bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    }
    /** 검증 대상 소켓. */
    private val socket = WriteBoundedSocket(WRITE_TIMEOUT_MILLIS).apply { sendBufferSize = SMALL_BUFFER }

    @After
    fun tearDown() {
        runCatching { socket.close() }
        server.close()
    }

    // 제한이 동작하지 않으면 쓰기가 끝나지 않으므로 테스트 시간을 제한한다
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `받는 쪽이 읽지 않아 쓰기가 막히면 제한 시간에 끝내고 소켓을 닫는다`() {
        val peer = connect()

        peer.use {
            assertThrows(SocketTimeoutException::class.java) { writeRepeatedly(STALL_BYTES) }
        }

        assertTrue(socket.isClosed)
    }

    @Test
    fun `받는 쪽이 계속 읽으면 제한 시간보다 오래 걸리는 전송도 끊지 않는다`() {
        val peer = connect()
        val reader = thread { peer.use { it.getInputStream().readBytes() } }

        // 쓰기 사이를 띄워 전체 전송은 제한 시간보다 오래 걸리지만, 쓰기 한 번은 그 안에 끝난다 — 끝난 쓰기의 제한이 남아 있으면 소켓이 닫힌다
        repeat(SLOW_WRITE_COUNT) {
            socket.getOutputStream().write(ByteArray(SMALL_BUFFER))
            Thread.sleep(WRITE_TIMEOUT_MILLIS / 4)
        }
        socket.shutdownOutput()
        reader.join(JOIN_TIMEOUT_MILLIS)

        assertFalse(socket.isClosed)
    }

    /** [socket]을 [server]에 연결하고 받은 쪽 소켓을 돌려준다. */
    private fun connect(): Socket {
        socket.connect(server.localSocketAddress)
        return server.accept()
    }

    /** [socket]에 [total]바이트를 버퍼 단위로 쓴다. */
    private fun writeRepeatedly(total: Int) {
        val chunk = ByteArray(CHUNK_BYTES)
        repeat(total / CHUNK_BYTES) { socket.getOutputStream().write(chunk) }
    }

    private companion object {
        /** 쓰기 한 번의 제한 시간(ms). */
        const val WRITE_TIMEOUT_MILLIS = 300L
        /** 소켓 송수신 버퍼 크기. */
        const val SMALL_BUFFER = 4 * 1024
        /** 한 번에 쓰는 크기. */
        const val CHUNK_BYTES = 64 * 1024
        /** 받는 쪽이 읽지 않을 때 쓰려는 전체 크기. 두 버퍼를 합친 것보다 훨씬 커서 반드시 막힌다. */
        const val STALL_BYTES = 64 * 1024 * 1024
        /** 천천히 읽히는 전송에서 쓰는 횟수. 횟수 × 쉬는 시간이 제한 시간을 넘는다. */
        const val SLOW_WRITE_COUNT = 8
        /** 받는 쪽 스레드가 끝나기를 기다리는 상한(ms). */
        const val JOIN_TIMEOUT_MILLIS = 5_000L
        /** 쓰기가 끝나지 않을 때 테스트를 실패로 끝내는 상한(ms). */
        const val TEST_TIMEOUT_MILLIS = 10_000L
    }
}
