package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files

/**
 * 데이터를 주고받는 중에 취소되거나 구현 오류가 나면 [FtpTransferrer]가 결과로 바꾸지 않고 전파하며,
 * 세션을 지킬 수 있으면 지키고(취소 후 완료 응답을 읽음) 지킬 수 없으면 한 번만 종료하는지 검증하는 단위 테스트.
 */
class FtpTransferrerInterruptedTransferTest {

    /** 받은 파일을 저장할 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-interrupted-transfer-test").toFile()
    /** 데이터 스트림을 내려주는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { streamContent = "data".toByteArray() }
    /** 검증 대상 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null) } }
    /** 검증 대상 전송기. */
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session))
    /** 다운로드 대상 로컬 파일. */
    private val localFile = File(dir, "a.txt")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `전송 중 구현 오류는 전파하고 완료 응답을 기다리지 않으며 세션을 한 번만 종료한다`() {
        client.streamReadFailure = IllegalStateException("구현 오류")

        assertThrows(IllegalStateException::class.java) { runBlocking { download() } }

        assertEquals(0, client.completePendingCalls)
        assertFalse(runBlocking { session.isReady() })
        assertEquals(1, client.disconnectCalls)
    }

    @Test
    fun `전송 중 취소되면 받다 만 파일을 지우고 완료 응답을 읽어 세션을 유지한다`() {
        client.streamReadFailure = CancellationException("취소")

        assertThrows(CancellationException::class.java) { runBlocking { download() } }

        assertFalse("받다 만 파일은 지워야 함", localFile.exists())
        assertEquals(1, client.completePendingCalls)
        assertTrue(runBlocking { session.isReady() })
        assertEquals(0, client.disconnectCalls)
    }

    @Test
    fun `전송 중 취소된 뒤 완료 응답을 읽지 못하면 응답 순서를 잃었으므로 세션을 종료한다`() {
        client.streamReadFailure = CancellationException("취소")
        client.onCompletePending = { throw SocketTimeoutException("Read timed out") }

        assertThrows(CancellationException::class.java) { runBlocking { download() } }

        assertFalse(runBlocking { session.isReady() })
        assertEquals(1, client.disconnectCalls)
    }

    /** 원격 파일 하나를 [localFile]로 받는다. */
    private suspend fun download() = transferrer.download("ftp://host/r/a.txt", localFile.path)
}
