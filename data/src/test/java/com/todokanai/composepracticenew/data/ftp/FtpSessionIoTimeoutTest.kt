package com.todokanai.composepracticenew.data.ftp

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [FtpSession]이 소켓 상한을 걸 수 없는 설정을 받아들이지 않는지 검증하는 테스트.
 * 상한이 될 수 없는 값(0 이하)으로는 만들어지지 않고, 상한이 걸리지 않는 능동 모드 데이터 연결은 열지 않는다.
 */
class FtpSessionIoTimeoutTest {

    @Test
    fun `상한이 0이면 세션을 만들지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) { FtpSession(SessionFtpClient(), ioTimeoutMillis = 0) }
    }

    @Test
    fun `상한이 음수면 세션을 만들지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) { FtpSession(SessionFtpClient(), ioTimeoutMillis = -1) }
    }

    // 거부하지 못하면 서버가 접속해 오지 않는 데이터 연결을 기다리므로 테스트 시간을 제한한다
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `수동 모드로 바꾸지 않은 채 데이터 연결을 열면 구현 오류로 바로 실패한다`() {
        val client = SessionFtpClient()
        FtpSession(client, ioTimeoutMillis = ScriptedFtpServer.TIMEOUT_MILLIS)

        ScriptedFtpServer().use { server ->
            client.connect(server.host, server.port)
            try {
                assertThrows(IllegalStateException::class.java) { client.retrieveFileStream("a.bin") }
            } finally {
                runCatching { client.disconnect() }
            }
        }
    }

    private companion object {
        /** 데이터 연결을 거부하지 못했을 때 테스트를 실패로 끝내는 상한(ms). */
        const val TEST_TIMEOUT_MILLIS = 10_000L
    }
}
