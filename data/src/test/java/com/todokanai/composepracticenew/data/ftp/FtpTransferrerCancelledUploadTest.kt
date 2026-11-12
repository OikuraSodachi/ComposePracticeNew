package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
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
 * 업로드가 취소되면 [FtpTransferrer]가 완료 응답을 읽은 뒤 서버에 남았을 수 있는 받다 만 파일을 DELE로 지우고,
 * 지우지 못하면 이유(거부 응답, 세션 종료)와 함께 남았을 수 있다고 알리며, 세션이 이미 끝났으면 지우기를 보내지 않고, 취소는 결과로 바꾸지 않고 전파하는지 검증하는 단위 테스트(#236).
 * 취소는 오케스트레이터처럼 진행 보고 콜백이 CancellationException을 던지는 것으로 흉내 낸다.
 */
class FtpTransferrerCancelledUploadTest {

    /** 올릴 파일을 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-cancelled-upload-test").toFile()
    /** 올릴 로컬 파일. */
    private val localFile = File(dir, "a.bin").apply { writeText("data") }
    /** STOR를 받아들이는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { streamContent = ByteArray(0) }
    /** 검증 대상 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }
    /** 넣은 실패 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingNotices()
    /** 검증 대상 전송기. */
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), notices)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `업로드 중 취소되면 완료 응답을 읽은 뒤 받다 만 파일을 지우고 알리지 않는다`() {
        assertThrows(CancellationException::class.java) { runBlocking { uploadCancelledMidway() } }

        assertEquals(listOf("STOR", "DELE"), client.sentCommands.filter { it == "STOR" || it == "DELE" })
        assertEquals(1, client.completePendingCalls)
        assertEquals(client.storeRequests, client.deleteRequests)
        assertTrue(notices.failures.isEmpty())
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `서버가 지우기를 거부하면 받다 만 파일이 남았을 수 있다고 거부 응답과 함께 알린다`() {
        client.onDeleteFile = { false }

        assertThrows(CancellationException::class.java) { runBlocking { uploadCancelledMidway() } }

        assertEquals(
            listOf("업로드 취소: 서버에 받다 만 파일이 남았을 수 있습니다 (${client.storeRequests.single()}) — DELE 실패: 550 Permission denied"),
            notices.failures
        )
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `완료 응답을 읽지 못해 세션을 끝냈으면 지우기를 보내지 않고 남았을 수 있다고 알린다`() {
        client.onCompletePending = { throw SocketTimeoutException("Read timed out") }

        assertThrows(CancellationException::class.java) { runBlocking { uploadCancelledMidway() } }

        assertTrue(client.deleteRequests.isEmpty())
        assertEquals(
            listOf("업로드 취소: 서버에 받다 만 파일이 남았을 수 있습니다 (${client.storeRequests.single()}) — 연결이 끊겨 지우지 못했습니다"),
            notices.failures
        )
        assertFalse(runBlocking { session.isReady() })
    }

    @Test
    fun `지우기의 응답을 읽지 못하면 세션을 끝내고 남았을 수 있다고 알린다`() {
        client.onDeleteFile = { throw SocketTimeoutException("Read timed out") }

        assertThrows(CancellationException::class.java) { runBlocking { uploadCancelledMidway() } }

        assertEquals(
            listOf("업로드 취소: 서버에 받다 만 파일이 남았을 수 있습니다 (${client.storeRequests.single()}) — 연결이 끊겨 지우지 못했습니다"),
            notices.failures
        )
        assertFalse(runBlocking { session.isReady() })
    }

    /** [localFile]을 올리다 첫 진행 보고에서 취소한다. */
    private suspend fun uploadCancelledMidway() =
        transferrer.upload(localFile.path, "ftp://host/a.bin") { state: ProgressState ->
            if ((state.writtenBytes ?: 0) > 0) throw CancellationException("취소")
        }

    /** 넣은 실패 메시지를 넣은 순서대로 기록하는 테스트용 메시지 저장소. */
    private class RecordingNotices : OperationNoticeReporter {
        val failures = mutableListOf<String>()
        override fun report(kind: OperationNoticeKind, message: String) {
            if (kind == OperationNoticeKind.FAILURE) failures += message
        }
    }
}
