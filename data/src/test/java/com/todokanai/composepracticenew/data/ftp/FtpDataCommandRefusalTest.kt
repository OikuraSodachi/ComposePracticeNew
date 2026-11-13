package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer.Companion.TIMEOUT_MILLIS
import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.OperationNotices
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 데이터 연결을 쓰는 명령이 데이터 연결을 열기 전에 거부되면, 보낸 데이터 명령이 아니라 실제로 거부된 명령(준비 명령 PASV 포함)의 이름으로 알리는지 검증하는 테스트(#268).
 * 실제 commons-net FTPClient로 접속해, 준비 명령이 거부되면 데이터 명령을 보내지 않는다는 commons-net의 동작도 함께 고정한다.
 */
class FtpDataCommandRefusalTest {

    /** 테스트마다 만드는 임시 로컬 디렉터리. 테스트가 끝나면 지운다. */
    private val dir: File = Files.createTempDirectory("ftp-data-refusal-test").toFile()
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
    fun `목록 조회의 PASV가 거부되면 LIST가 아니라 PASV 실패로 알리고 세션을 유지한다`() {
        val scripted = ScriptedFtpServer(mapOf("PASV" to { exchange -> exchange.send("500 PASV not understood") })).also { server = it }
        val session = connectedSession(scripted)
        val executor = FtpCommandExecutor(session, DataCommandRunner(session))

        val result = runBlocking { executor.listFiles("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("PASV 실패: 500 PASV not understood"), result)
        assertFalse("준비 명령이 거부되면 LIST를 보내지 않아야 함", "LIST" in scripted.received)
        assertTrue("세션이 유지돼야 함", runBlocking { session.isReady() })
    }

    @Test
    fun `다운로드의 PASV가 거부되면 RETR가 아니라 PASV 실패로 알린다`() {
        val scripted = ScriptedFtpServer(mapOf("PASV" to { exchange -> exchange.send("500 PASV not understood") })).also { server = it }

        val reason = downloadFailureReason(scripted)

        assertEquals("PASV 실패: 500 PASV not understood", reason)
        assertFalse("준비 명령이 거부되면 RETR를 보내지 않아야 함", "RETR" in scripted.received)
    }

    @Test
    fun `다운로드의 RETR가 거부되면 RETR 실패로 알린다`() {
        val scripted = ScriptedFtpServer(mapOf("RETR" to { exchange -> exchange.send("550 No such file") })).also { server = it }

        val reason = downloadFailureReason(scripted)

        assertEquals("RETR 실패: 550 No such file", reason)
    }

    /**
     * [scripted]에 접속해 파일 하나를 내려받고, 그 파일의 실패 이유를 돌려준다.
     * @param scripted 접속할 서버
     * @return 내려받은 파일의 실패 이유
     */
    private fun downloadFailureReason(scripted: ScriptedFtpServer): String? {
        val session = connectedSession(scripted)
        val transferrer = FtpTransferrer(session, FtpCommandExecutor(session, DataCommandRunner(session)), DataCommandRunner(session), OperationNotices())
        val result = runBlocking {
            transferrer.download(sources = listOf(DownloadSource("ftp://host/r/a.txt", File(dir, "a.txt").path, false)), resolveConflict = UnexpectedConflictResolver)
        }
        assertTrue("세션이 유지돼야 함", runBlocking { session.isReady() })
        return (result as IoResult.PartiallyFailed).failures.single().reason
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
        return FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }
    }
}
