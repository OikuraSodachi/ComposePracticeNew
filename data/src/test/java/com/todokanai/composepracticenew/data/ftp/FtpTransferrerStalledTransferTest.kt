package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer.Companion.TIMEOUT_MILLIS
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 데이터 연결에서 서버가 멈추면 [FtpTransferrer]가 끝없이 기다리지 않고 [FtpSession]의 I/O 상한에 그 항목만 실패로 끝내며,
 * 서버가 완료 응답을 보내면 세션을 유지하는지 검증하는 통합 테스트(#224).
 * 실제 commons-net FTPClient로 로컬 소켓의 정해진 응답 서버에 수동(PASV) 모드로 접속한다.
 */
class FtpTransferrerStalledTransferTest {

    /** 받거나 올릴 파일을 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-stalled-transfer-test").toFile()
    /** 검증 대상이 쓰는 실제 FTP 클라이언트. 데이터 소켓의 송신 버퍼를 작게 해 서버가 읽지 않으면 쓰기가 곧 막히게 한다. */
    private val client = FTPClient().apply { sendDataSocketBufferSize = SMALL_BUFFER }
    /** 검증 대상 세션. 데이터 연결의 읽기·쓰기 상한을 짧게 둔다. */
    private val session = FtpSession(client, ioTimeoutMillis = IO_TIMEOUT_MILLIS)
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runCatching { client.disconnect() }
        server?.close()
        dir.deleteRecursively()
    }

    // 상한이 동작하지 않으면 전송이 끝나지 않으므로 테스트 시간을 제한한다
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `다운로드 중 서버가 데이터를 보내지 않으면 읽기 상한에 그 파일만 실패하고 세션을 유지한다`() {
        connect("RETR" to { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Opening data connection")
                // 데이터를 보내지 않고, 클라이언트가 데이터 연결을 닫을 때까지 기다린다
                socket.getInputStream().read()
            }
            exchange.send("426 Transfer aborted")
        })

        val result = runBlocking { FtpTransferrer(session, FtpCommandExecutor(session)).download("ftp://host/a.bin", File(dir, "a.bin").path) }

        assertEquals(IoResult.ItemFailed("Read timed out"), result)
        assertTrue(runBlocking { session.isReady() })
    }

    // 상한이 동작하지 않으면 전송이 끝나지 않으므로 테스트 시간을 제한한다
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `업로드 중 서버가 받기를 멈추면 쓰기 상한에 그 파일만 실패하고 세션을 유지한다`() {
        val localFile = File(dir, "a.bin").apply { writeBytes(ByteArray(STALL_BYTES)) }
        connect("STOR" to { exchange ->
            exchange.data.receiveBufferSize = SMALL_BUFFER
            exchange.data.accept().use {
                exchange.send("150 Ok to send data")
                // 받기를 멈춘다. 클라이언트의 쓰기 상한보다 오래 기다린 뒤 전송을 끝낸다
                Thread.sleep(IO_TIMEOUT_MILLIS * 4L)
            }
            exchange.send("426 Transfer aborted")
        })

        val result = runBlocking { FtpTransferrer(session, FtpCommandExecutor(session)).upload(localFile.path, "ftp://host/a.bin") }

        assertEquals(IoResult.ItemFailed("Write timed out"), result)
        assertTrue(runBlocking { session.isReady() })
    }

    /**
     * [handler]대로 응답하는 서버에 접속해 세션을 로그인 상태로 둔다.
     * 제어 연결의 응답 대기는 서버가 늦게 보내는 완료 응답을 받을 만큼 길게 다시 둔다 — 이 테스트는 데이터 연결의 상한만 검증한다.
     * @param handler 전송 명령과 그 응답 동작
     */
    private fun connect(handler: Pair<String, (ScriptedFtpServer.Exchange) -> Unit>) {
        val scripted = ScriptedFtpServer(mapOf(handler)).also { server = it }
        client.connect(scripted.host, scripted.port)
        client.soTimeout = TIMEOUT_MILLIS
        client.enterLocalPassiveMode()
        runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null) }
    }

    private companion object {
        /** 세션의 소켓 읽기·쓰기 상한(ms). */
        const val IO_TIMEOUT_MILLIS = 300
        /** 데이터 소켓 송수신 버퍼 크기. */
        const val SMALL_BUFFER = 4 * 1024
        /** 서버가 받지 않을 때 올리려는 파일 크기. 두 버퍼를 합친 것보다 훨씬 커서 반드시 막힌다. */
        const val STALL_BYTES = 16 * 1024 * 1024
        /** 전송이 끝나지 않을 때 테스트를 실패로 끝내는 상한(ms). */
        const val TEST_TIMEOUT_MILLIS = 15_000L
    }
}
