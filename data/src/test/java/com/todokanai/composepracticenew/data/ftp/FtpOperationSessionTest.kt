package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RemoteStep
import com.todokanai.composepracticenew.repository.OperationNotices
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 진행 표시가 있는 원격 작업이 자기 세션([FtpSessionManager.withOperationSession])에서 실행되는지 검증하는 통합 테스트(#436).
 * 접속은 진행 중인 작업의 I/O를 기다리지 않고, 작업은 요청할 때 묶은 서버에서만 실행되며, 작업 세션의 끊김·거부는 탐색 세션의 연결 상태에 나오지 않는다.
 * 실제 FTPClient로 여러 연결을 동시에 받는 로컬 테스트 서버에 접속한다.
 */
class FtpOperationSessionTest {

    @get:Rule val folder = TemporaryFolder()

    /** 세션 관리자로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture(keepAliveIntervalMillis = KEEPALIVE_MILLIS)
    /** 검증 대상. */
    private val manager = fixture.manager
    /** 붙잡아 둔 STOR의 완료 응답을 보내게 한다. */
    private val release = CountDownLatch(1)

    @After
    fun tearDown() {
        release.countDown()
        fixture.close()
    }

    @Test
    fun `업로드가 완료 응답을 기다리는 동안에도 다른 서버에 바로 접속한다`() {
        val uploading = serverHoldingFirstStor()
        val other = fixture.server()
        val upload = startUpload(connectedToken(uploading), "a.txt")
        awaitStor(uploading)

        val result = AtomicReference<ConnectResult?>()
        thread { result.set(fixture.connect(other, name = "other")) }.join(1_500)

        assertEquals(ConnectResult.Connected, result.get())
        release.countDown()
        assertEquals(IoResult.Done, upload.get())
    }

    @Test
    fun `업로드 중 다른 서버에 접속해도 남은 항목은 원래 서버로 간다`() {
        val uploading = serverHoldingFirstStor()
        val other = fixture.server(mapOf("STOR" to { it.send("550 not here") }))
        val upload = startUpload(connectedToken(uploading), "a.txt", "b.txt")
        awaitStor(uploading)

        fixture.connect(other, name = "other")
        release.countDown()

        assertEquals(IoResult.Done, upload.get())
        assertFalse(other.received.toString(), "STOR" in other.received)
        assertEquals(2, uploading.received.count { it == "STOR" })
    }

    @Test
    fun `요청한 뒤 탐색 세션이 끝나도 작업은 묶은 서버에 자기 연결을 열어 실행한다`() {
        val server = fixture.server(storing())
        val token = connectedToken(server)
        runBlocking { manager.disconnect() }

        val result = upload(token, "a.txt")

        assertEquals(IoResult.Done, result)
        assertEquals(1, server.received.count { it == "STOR" })
    }

    @Test
    fun `앱이 연결을 더 유지할 수 없어 탐색 세션을 끝내도 진행 중인 작업은 계속된다`() {
        val uploading = serverHoldingFirstStor()
        val upload = startUpload(connectedToken(uploading), "a.txt")
        awaitStor(uploading)

        assertTrue(runBlocking { manager.drop() })
        release.countDown()

        assertEquals(IoResult.Done, upload.get())
        assertEquals(RemoteConnectionState.Lost, manager.connectionState.value)
    }

    @Test
    fun `앱이 종료를 요청해도 진행 중인 작업은 계속된다`() {
        val uploading = serverHoldingFirstStor()
        val upload = startUpload(connectedToken(uploading), "a.txt")
        awaitStor(uploading)

        runBlocking { manager.disconnect() }
        release.countDown()

        assertEquals(IoResult.Done, upload.get())
    }

    @Test
    fun `작업 세션이 끊기면 그 작업만 끊김으로 끝나고 연결 상태와 끊김 알림에는 나오지 않는다`() {
        val server = fixture.server(mapOf("STOR" to { it.hangUp() }))
        val token = connectedToken(server)

        val result = upload(token, "a.txt")

        assertTrue(result.toString(), result is IoResult.ConnectionLost)
        assertEquals(RemoteConnectionState.Connected("server"), manager.connectionState.value)
        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `서버가 작업용 연결을 접속 인사의 421로 거부하면 그 작업만 거부 응답을 담아 실패한다`() {
        val server = fixture.server(storing(), greeting = { index -> if (index == 0) "220 ready" else "421 Too many connections" })
        val token = connectedToken(server)

        val result = upload(token, "a.txt")

        assertEquals(IoResult.ConnectionLost(FailureReason.ServerRefused(RemoteStep.OPEN_SESSION, "421 Too many connections")), result)
        assertEquals(RemoteConnectionState.Connected("server"), manager.connectionState.value)
        assertEquals(0, fixture.notices.pending.value.size)
    }

    @Test
    fun `서버가 작업용 연결의 로그인을 거부하면 그 작업만 거부 응답을 담아 실패한다`() {
        val refusingSecondLogin = mapOf<String, (ScriptedFtpServer.Exchange) -> Unit>(
            "PASS" to { if (it.connectionIndex == 0) it.send("230 Logged in") else it.send("530 Too many users") }
        )
        val server = fixture.server(storing() + refusingSecondLogin)
        val token = connectedToken(server)

        val result = upload(token, "a.txt")

        assertEquals(IoResult.ConnectionLost(FailureReason.ServerRefused(RemoteStep.OPEN_SESSION, "530 Too many users")), result)
        assertFalse("SYST" in server.received.drop(server.received.indexOfLast { it == "PASS" }))
        assertEquals(RemoteConnectionState.Connected("server"), manager.connectionState.value)
    }

    @Test
    fun `작업용 연결에서 목록 형식을 알 수 없으면 그 작업만 그 원인으로 실패한다`() {
        val server = fixture.server(mapOf("SYST" to { if (it.connectionIndex == 0) it.send("215 UNIX Type: L8") else it.send("215 PLAN9") }))
        val token = connectedToken(server)

        val result = upload(token, "a.txt")

        assertEquals(IoResult.ConnectionLost(FailureReason.RemoteStepFailed(RemoteStep.OPEN_SESSION, FailureReason.ListingFormatUnknown)), result)
        assertEquals(RemoteConnectionState.Connected("server"), manager.connectionState.value)
    }

    @Test
    fun `작업용 연결을 여는 동안 취소하면 소켓을 닫아 바로 끝낸다`() {
        // 작업용 연결에는 접속 인사를 보내지 않아, 응답 읽기 상한까지 기다리게 한다
        val server = fixture.server(greeting = { index -> if (index == 0) "220 ready" else null })
        val token = connectedToken(server)

        val elapsedMillis = runBlocking(Dispatchers.IO) {
            val operation = async(start = CoroutineStart.UNDISPATCHED) { manager.withOperationSession(token) { IoResult.Done } }
            awaitUntil("작업용 연결을 받지") { server.connectionCount == 2 }
            val started = System.nanoTime()
            operation.cancel()
            operation.join()
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        }

        assertTrue("취소가 응답 읽기 상한을 기다렸다: ${elapsedMillis}ms", elapsedMillis < 1_000)
        assertFalse(fixture.clients[1].isConnected)
    }

    @Test
    fun `작업이 끝나면 작업 세션을 닫는다`() {
        val server = fixture.server(storing())
        val token = connectedToken(server)

        upload(token, "a.txt")

        assertFalse(fixture.clients[1].isConnected)
        // 탐색 세션은 열려 있으므로 받은 QUIT은 작업 세션의 것이다
        assertTrue(server.received.toString(), "QUIT" in server.received)
        assertEquals(RemoteConnectionState.Connected("server"), manager.connectionState.value)
    }

    @Test
    fun `작업 세션은 작업이 명령 없이 기다리는 동안 Keep-Alive를 보낸다`() {
        // 탐색 세션 없이 작업 세션만 열어, 받은 NOOP이 작업 세션의 것이게 한다
        val server = fixture.server(mapOf("NOOP" to { it.send("200 NOOP ok") }))
        val token = RemoteSession(1, fixture.itemOf(server))

        // 다운로드가 같은 이름 질문의 답을 기다리는 것처럼, 명령 없이 Keep-Alive 간격보다 오래 기다린다
        val result = runBlocking { manager.withOperationSession(token) { delay(KEEPALIVE_MILLIS * 4); IoResult.Done } }

        assertEquals(IoResult.Done, result)
        assertTrue(server.received.toString(), "NOOP" in server.received)
    }

    @Test
    fun `요청한 뒤 다른 서버에 접속했으면 탐색 세션의 작업은 실행하지 않는다`() {
        val server = fixture.server()
        val other = fixture.server(mapOf("MKD" to { it.send("257 created") }))
        val token = connectedToken(server)
        fixture.connect(other, name = "other")
        var ran = false

        val result = runBlocking { manager.withBrowsingSession(token) { parts -> ran = true; parts.executor.makeDirectory("ftp://host/new") } }

        assertEquals(IoResult.ConnectionLost(FailureReason.NotConnected), result)
        assertFalse(ran)
        assertFalse("MKD" in other.received)
    }

    /**
     * [server]에 탐색 세션으로 접속하고 그 세션의 표지를 돌려준다.
     * @param server 접속할 서버
     * @return 연결된 세션의 표지
     */
    private fun connectedToken(server: ScriptedFtpServer): RemoteSession {
        assertEquals(ConnectResult.Connected, fixture.connect(server))
        return manager.current()!!
    }

    /**
     * [token]의 작업 세션으로 [names] 파일을 올린다.
     * @param token 작업을 묶은 세션 표지
     * @param names 올릴 로컬 파일 이름
     * @return 업로드 결과
     */
    private fun upload(token: RemoteSession, vararg names: String): IoResult<Unit> {
        val sources = names.map { UploadSource(localFile(it).path, "ftp://host/$it") }
        return runBlocking { manager.withOperationSession(token) { it.transferrer(OperationNotices()).upload(sources) } }
    }

    /**
     * 다른 스레드에서 [upload]를 시작한다.
     * @return 업로드 결과를 기다려 돌려주는 동작
     */
    private fun startUpload(token: RemoteSession, vararg names: String): PendingResult {
        val result = AtomicReference<IoResult<Unit>?>()
        val worker = thread { result.set(upload(token, *names)) }
        return PendingResult { worker.join(ScriptedFtpServer.TIMEOUT_MILLIS * 2L); result.get() }
    }

    /** 다른 스레드에서 실행 중인 작업의 결과. [get]은 끝나기를 기다린다. */
    private fun interface PendingResult {
        /** 작업이 끝나기를 기다려 결과를 돌려준다. 기한 안에 끝나지 않으면 null. */
        fun get(): IoResult<Unit>?
    }

    /**
     * STOR를 받아들이는 응답 동작. 데이터를 끝까지 받고 226으로 끝낸다.
     * @return 명령 이름 → 응답 동작
     */
    private fun storing(): Map<String, (ScriptedFtpServer.Exchange) -> Unit> = mapOf("STOR" to { exchange ->
        exchange.data.accept().use { socket ->
            exchange.send("150 Ok to send data")
            socket.getInputStream().readBytes()
        }
        exchange.send("226 Transfer complete")
    })

    /**
     * 첫 STOR의 데이터를 받은 뒤 [release]까지 완료 응답을 미루는 서버를 띄운다. 그동안 업로드는 작업 세션의 잠금을 쥐고 응답을 기다린다.
     * @return 띄운 서버
     */
    private fun serverHoldingFirstStor(): ScriptedFtpServer {
        var stored = 0
        return fixture.server(mapOf("STOR" to { exchange ->
            exchange.data.accept().use { socket ->
                exchange.send("150 Ok to send data")
                socket.getInputStream().readBytes()
            }
            if (stored++ == 0) release.await(ScriptedFtpServer.TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            exchange.send("226 Transfer complete")
        }))
    }

    /**
     * [server]가 STOR를 받을 때까지 기다린다.
     * @param server 기다릴 서버
     */
    private fun awaitStor(server: ScriptedFtpServer) = awaitUntil("STOR를 받지") { "STOR" in server.received }

    /**
     * 내용이 있는 로컬 파일을 만든다.
     * @param name 파일 이름
     * @return 만든 파일
     */
    private fun localFile(name: String): File = File(folder.root, name).apply { if (!exists()) writeText("data") }

    private companion object {
        /** 테스트의 Keep-Alive 간격(ms). */
        const val KEEPALIVE_MILLIS = 100L
    }
}
