package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.repository.OperationNotices
import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer.Companion.TIMEOUT_MILLIS
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

/**
 * 데이터 연결에서 서버가 멈추면 [FtpTransferrer]가 끝없이 기다리지 않고 [FtpSession]의 I/O 상한에 그 항목만 실패로 끝내며,
 * 서버가 완료 응답을 보내면 세션을 유지하는지 검증하는 통합 테스트(#224).
 * 업로드를 중단하면 서버가 잘린 파일을 다 받은 것으로 보지 않도록, 데이터 연결을 파일 끝(정상 닫기)이 아니라 리셋으로 끝내는지도 검증한다.
 * 실제 commons-net FTPClient로 로컬 소켓의 정해진 응답 서버에 수동(PASV) 모드로 접속한다.
 */
class FtpTransferrerStalledTransferTest {

    /** 받거나 올릴 파일을 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-stalled-transfer-test").toFile()
    /** 검증 대상이 쓰는 실제 FTP 클라이언트. 데이터 소켓의 송신 버퍼를 작게 해 서버가 읽지 않으면 쓰기가 곧 막히게 한다. */
    private val client = SessionFtpClient().apply { sendDataSocketBufferSize = SMALL_BUFFER }
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
    fun `다운로드 중 서버가 데이터를 보내지 않으면 읽기 상한에 그 파일만 실패하고 데이터 연결을 리셋한다`() {
        val serverEnd = AtomicReference<DataEnd>()
        connect("RETR" to { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Opening data connection")
                // 데이터를 보내지 않고, 클라이언트가 데이터 연결을 닫을 때까지 기다린다
                serverEnd.set(readToEnd(socket))
            }
            exchange.send("426 Transfer aborted")
        })

        val result = runBlocking { FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices()).download("ftp://host/a.bin", File(dir, "a.bin").path, isDirectory = false, resolveConflict = UnexpectedConflictResolver) }

        assertEquals(IoResult.ItemFailed("Read timed out"), result)
        assertEquals(DataEnd.RESET, serverEnd.get())
        assertTrue(runBlocking { session.isReady() })
    }

    // 상한이 동작하지 않으면 전송이 끝나지 않으므로 테스트 시간을 제한한다
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `업로드 중 서버가 받기를 멈추면 쓰기 상한에 그 파일만 실패하고 서버는 파일 끝이 아니라 연결 리셋을 받는다`() {
        val localFile = File(dir, "a.bin").apply { writeBytes(ByteArray(STALL_BYTES)) }
        val serverEnd = AtomicReference<DataEnd>()
        connect("STOR" to { exchange ->
            exchange.data.receiveBufferSize = SMALL_BUFFER
            exchange.data.accept().use { socket ->
                exchange.send("150 Ok to send data")
                // 받기를 멈춘다. 클라이언트의 쓰기 상한보다 오래 기다린 뒤 다시 읽어, 클라이언트가 연결을 어떻게 끝냈는지 본다
                Thread.sleep(IO_TIMEOUT_MILLIS * 4L)
                serverEnd.set(readToEnd(socket))
            }
            exchange.send("426 Transfer aborted")
        })

        val result = runBlocking { FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices()).upload(localFile.path, "ftp://host/a.bin") }

        assertEquals(IoResult.ItemFailed("Write timed out"), result)
        assertEquals(DataEnd.RESET, serverEnd.get())
        assertTrue(runBlocking { session.isReady() })
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun `업로드를 전송 도중 실패로 멈추면 서버는 파일 끝이 아니라 연결 리셋을 받는다`() {
        val localFile = File(dir, "a.bin").apply { writeBytes(ByteArray(STALL_BYTES)) }
        val serverEnd = AtomicReference<DataEnd>()
        connect("STOR" to { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Ok to send data")
                serverEnd.set(readToEnd(socket))
            }
            exchange.send("426 Transfer aborted")
        })

        // 첫 진행률 보고에서 실패시켜, 쓰기 상한이 아닌 실패(로컬 파일 읽기 실패 등)로 전송이 멈춘 경우를 만든다
        val result = runBlocking {
            FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices()).upload(localFile.path, "ftp://host/a.bin") { throw IOException(LOCAL_FAILURE) }
        }

        assertEquals(IoResult.ItemFailed(LOCAL_FAILURE), result)
        assertEquals(DataEnd.RESET, serverEnd.get())
        assertTrue(runBlocking { session.isReady() })
    }

    /**
     * 받는 쪽 데이터 소켓을 끝까지 읽어, 보낸 쪽이 연결을 어떻게 끝냈는지 돌려준다.
     * @param socket 서버가 받은 데이터 연결
     * @return 정상 닫기(파일 끝)면 [DataEnd.EOF], 리셋이면 [DataEnd.RESET]
     */
    private fun readToEnd(socket: Socket): DataEnd = try {
        val input = socket.getInputStream()
        val buffer = ByteArray(SMALL_BUFFER)
        while (input.read(buffer) != -1) Unit
        DataEnd.EOF
    } catch (_: SocketException) {
        DataEnd.RESET
    }

    /** 보낸 쪽이 데이터 연결을 끝낸 방식. */
    private enum class DataEnd {
        /** 정상 닫기. stream 모드에서는 파일 끝(전송 완료)으로 읽힌다. */
        EOF,
        /** 연결 리셋. 전송 중단으로 읽힌다. */
        RESET
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
        runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
    }

    private companion object {
        /** 세션의 소켓 읽기·쓰기 상한(ms). */
        const val IO_TIMEOUT_MILLIS = 300
        /** 데이터 소켓 송수신 버퍼 크기. */
        const val SMALL_BUFFER = 4 * 1024
        /** 서버가 받지 않을 때 올리려는 파일 크기. 두 버퍼를 합친 것보다 훨씬 커서 반드시 막힌다. */
        const val STALL_BYTES = 16 * 1024 * 1024
        /** 전송 도중 일으키는 로컬 실패의 메시지. */
        const val LOCAL_FAILURE = "local read failed"
        /** 전송이 끝나지 않을 때 테스트를 실패로 끝내는 상한(ms). */
        const val TEST_TIMEOUT_MILLIS = 15_000L
    }
}
