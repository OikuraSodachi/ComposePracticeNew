package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer.Companion.TIMEOUT_MILLIS
import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import com.todokanai.composepracticenew.repository.OperationNotices
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files

/**
 * 수동 모드 데이터 소켓을 연결하지 못한 실패가 세션 끊김이 아니라 그 명령만의 실패인지 검증하는 테스트(#267).
 * 실제 commons-net FTPClient로, PASV가 아무도 받지 않는 포트를 알려 주는 서버에 접속한다 — 데이터 소켓 연결이 거부된다.
 * 서버가 명령(LIST·RETR)을 받지 않았는지도 확인해, commons-net이 데이터 소켓을 연결한 뒤에야 명령을 보낸다는 전제를 고정한다.
 */
class FtpDataConnectFailureTest {

    /** 테스트마다 만드는 임시 로컬 디렉터리. 테스트가 끝나면 지운다. */
    private val dir: File = Files.createTempDirectory("ftp-data-connect-test").toFile()
    /** 검증 대상이 쓰는 실제 FTP 클라이언트. */
    private val client = SessionFtpClient().apply { defaultTimeout = TIMEOUT_MILLIS }
    /** 테스트마다 띄운 서버. 테스트가 끝나면 닫는다. */
    private var server: ScriptedFtpServer? = null

    @After
    fun tearDown() {
        runCatching { client.disconnect() }
        server?.close()
        dir.deleteRecursively()
    }

    @Test
    fun `목록 조회의 데이터 소켓을 연결하지 못하면 그 조회만 실패하고 세션은 다음 조회에 쓸 수 있다`() {
        val refusedPort = unusedPort()
        var pasvCount = 0
        val scripted = ScriptedFtpServer(
            mapOf(
                // 첫 PASV만 아무도 받지 않는 포트를 알린다
                "PASV" to { exchange ->
                    val port = if (pasvCount++ == 0) refusedPort else exchange.data.localPort
                    exchange.send("227 Entering Passive Mode (127,0,0,1,${port / 256},${port % 256})")
                },
                "LIST" to { exchange ->
                    exchange.data.accept().use { socket ->
                        exchange.send("150 Here comes the directory listing")
                        socket.getOutputStream().write("-rw-r--r--   1 user group        1 Jan 01 00:00 a.txt\r\n".toByteArray())
                    }
                    exchange.send("226 Transfer complete")
                }
            )
        ).also { server = it }
        val session = connectedSession(scripted)
        val executor = FtpCommandExecutor(session, DataCommandRunner(session))

        val failed = runBlocking { executor.listFiles("ftp://host/r") }

        assertTrue("데이터 연결 실패로 알려야 함: $failed", (failed as IoResult.ItemFailed).message.orEmpty().startsWith("LIST 실패: 데이터 연결을 열지 못했습니다"))
        assertFalse("데이터 소켓을 연결하지 못했으면 LIST를 보내지 않아야 함", "LIST" in scripted.received)
        assertTrue("세션이 유지돼야 함", runBlocking { session.isReady() })
        assertEquals(RemoteConnectionState.Connected("server"), session.connectionState.value)

        val listed = runBlocking { executor.listFiles("ftp://host/r") }

        assertEquals(listOf("ftp://host/r/a.txt"), (listed as IoResult.Success).value.map { (it as ListedItem.Entry).entry.path })
    }

    @Test
    fun `다운로드의 데이터 소켓을 연결하지 못하면 그 파일만 실패하고 만든 빈 파일을 지운다`() {
        val refusedPort = unusedPort()
        val scripted = ScriptedFtpServer(
            mapOf("PASV" to { exchange -> exchange.send("227 Entering Passive Mode (127,0,0,1,${refusedPort / 256},${refusedPort % 256})") })
        ).also { server = it }
        val session = connectedSession(scripted)
        val transferrer = FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices())
        val localFile = File(dir, "a.txt")

        val result = runBlocking {
            transferrer.download(sources = listOf(DownloadSource("ftp://host/r/a.txt", localFile.path, false)), resolveConflict = UnexpectedConflictResolver)
        }

        val failure = (result as IoResult.PartiallyFailed).failures.single()
        assertTrue("데이터 연결 실패로 알려야 함: $result", failure.reason.orEmpty().startsWith("데이터 연결을 열지 못했습니다"))
        assertFalse("데이터 소켓을 연결하지 못했으면 RETR를 보내지 않아야 함", "RETR" in scripted.received)
        assertFalse("받기 시작하지 못한 다운로드의 빈 파일은 지워야 함", localFile.exists())
        assertTrue("세션이 유지돼야 함", runBlocking { session.isReady() })
    }

    @Test
    fun `데이터 소켓을 연결하지 못했을 때 세션이 이미 끝났으면 이 명령만의 실패가 아니라 예외로 전파한다`() {
        val cause = IOException("Socket closed")
        val runner = DataCommandRunner(EndedSession(cause))

        val thrown = runCatching { runBlocking { runner.run(FakeFtpClient(), open = { ByteArrayInputStream(ByteArray(0)) }) {} } }.exceptionOrNull()

        assertEquals(cause, thrown)
    }

    /**
     * [scripted]에 실제 클라이언트로 접속해 수동 모드로 로그인된 세션을 만든다.
     * @param scripted 접속할 서버
     * @return 로그인된 세션
     */
    private fun connectedSession(scripted: ScriptedFtpServer): FtpSession {
        client.connect(scripted.host, scripted.port)
        client.soTimeout = TIMEOUT_MILLIS
        client.enterLocalPassiveMode()
        return FtpSession(client, ConnectionLossNotices()).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }
    }

    /**
     * 지금 아무도 받지 않는 루프백 포트를 고른다. 그 포트로의 연결은 거부된다.
     * @return 받는 소켓이 없는 포트
     */
    private fun unusedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    /**
     * 잠금 밖 종료로 끝난 세션. 데이터 소켓을 연결하지 못한 것으로 답한다.
     * @param cause 데이터 소켓을 연결하지 못한 실패
     */
    private class EndedSession(private val cause: IOException) : FtpDataCommandSession {
        override fun <S : java.io.Closeable> openDataConnection(open: () -> S?): DataConnectionOpening<S> = DataConnectionOpening.NotConnected(cause)
        override suspend fun endUnusableSession() {}
        override suspend fun isReady(): Boolean = false
    }
}
