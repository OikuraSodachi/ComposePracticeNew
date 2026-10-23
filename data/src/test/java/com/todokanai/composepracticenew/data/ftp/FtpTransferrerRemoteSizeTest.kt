package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.MalformedServerReplyException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 다운로드 전 크기 조회(MLST)에서 응답을 끝까지 읽은 뒤의 항목 해석 실패만 "크기 모름"으로 넘기고,
 * 응답을 읽다 실패한 경우(형식 오류 포함)와 구현 오류는 삼키지 않는지 검증하는 단위 테스트.
 */
class FtpTransferrerRemoteSizeTest {

    /** 받은 파일을 저장할 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-remote-size-test").toFile()
    /** 데이터 스트림을 내려주는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { streamContent = "data".toByteArray() }
    /** 검증 대상 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn() } }
    /** 검증 대상 전송기. */
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `MLST 성공 응답에 항목 줄이 없으면 크기를 모르는 채로 전송하고 세션을 유지한다`() {
        client.onMlst = { listOf("250 End") }

        assertEquals(IoResult.Done, download())
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `항목 줄의 사실 값을 해석할 수 없으면 크기를 모르는 채로 전송하고 세션을 유지한다`() {
        listOf(" size=abc;type=file; a.txt", " type=file;unix.mode=7; a.txt").forEach { entryLine ->
            client.onMlst = { listOf("250-Listing a.txt", entryLine, "250 End") }

            assertEquals(entryLine, IoResult.Done, download())
            assertTrue(entryLine, runBlocking { session.isReady() })
        }
    }

    @Test
    fun `MLST 응답을 읽다 형식 오류가 나면 응답 순서를 잃었으므로 연결 끊김으로 알리고 세션을 종료한다`() {
        client.onMlst = { throw MalformedServerReplyException("Truncated server reply: '25'") }

        assertTrue(download() is IoResult.ConnectionLost)
        assertFalse(runBlocking { session.isReady() })
        assertTrue("전송을 시작하지 않아야 함", client.retrieveRequests.isEmpty())
    }

    @Test
    fun `크기 조회 중 구현 오류는 크기 모름으로 삼키지 않고 전파한다`() {
        client.onMlst = { throw IllegalStateException("구현 오류") }

        assertThrows(IllegalStateException::class.java) { download() }
        assertFalse(runBlocking { session.isReady() })
    }

    /** 원격 파일 하나를 받는다. */
    private fun download(): IoResult<Unit> = runBlocking { transferrer.download("ftp://host/r/a.txt", File(dir, "a.txt").path) }
}
