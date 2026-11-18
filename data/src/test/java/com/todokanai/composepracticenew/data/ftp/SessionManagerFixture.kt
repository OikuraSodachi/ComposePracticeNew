package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.fail
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * [FtpSessionManager]를 실제 FTP 클라이언트로 로컬 테스트 서버([ScriptedFtpServer])에 접속시키는 테스트 도구.
 * 소켓을 닫을 때의 예외와 응답 해석은 commons-net 내부 동작이므로 가짜 클라이언트 대신 실제 클라이언트를 쓴다.
 * 서버 스크립트가 응답하지 않을 때 오래 기다리지 않도록 세션의 I/O 상한을 짧게 둔다.
 * @param keepAliveIntervalMillis 세션마다 NOOP을 보내는 간격(ms). 기본값은 테스트 중에 보내지 않을 만큼 길다
 */
internal class SessionManagerFixture(keepAliveIntervalMillis: Long = 60_000) : Closeable {

    /** 관리자가 끊김을 넣는 알림함. */
    val notices = ConnectionLossNotices()
    /** 관리자의 Keep-Alive·바꾼 세션의 종료를 실행하는 스코프. 끝나면 취소한다. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 관리자가 세션마다 만든 클라이언트. 만든 순서대로 쌓는다. */
    val clients = CopyOnWriteArrayList<SessionFtpClient>()
    /** 다음 세션의 클라이언트를 만드는 동작. 명령 사이에 끼어들어야 하는 테스트가 바꾼다. */
    @Volatile var clientFactory: () -> SessionFtpClient = { SessionFtpClient() }
    /** 검증 대상. */
    val manager = FtpSessionManager(notices, scope, { clientFactory().also { clients += it } }, ScriptedFtpServer.TIMEOUT_MILLIS, keepAliveIntervalMillis)
    /** 띄운 서버. 끝나면 닫는다. */
    private val servers = mutableListOf<ScriptedFtpServer>()

    /**
     * 테스트 서버를 띄운다. 끝나면 이 도구가 닫는다.
     * @param script 기본 응답을 덮어쓸 명령별 응답 동작
     * @param greeting 연결마다 보낼 접속 인사([ScriptedFtpServer]의 같은 이름 인자)
     * @return 띄운 서버
     */
    fun server(
        script: Map<String, (ScriptedFtpServer.Exchange) -> Unit> = emptyMap(),
        greeting: (Int) -> String? = { "220 ready" }
    ): ScriptedFtpServer = ScriptedFtpServer(script, greeting).also { servers += it }

    /**
     * [server]를 가리키는 저장된 서버 정보를 만든다.
     * @param server 접속할 테스트 서버
     * @param name 서버의 표시 이름
     * @return 저장된 서버 정보
     */
    fun itemOf(server: ScriptedFtpServer, name: String = "server"): RemoteStorageItem =
        RemoteStorageItem(name = name, address = server.host, port = server.port, userId = "user", password = "password", settings = RemoteProtocolSettings.Ftp("UTF-8"))

    /**
     * [server]에 탐색 세션으로 접속한다.
     * @param server 접속할 테스트 서버
     * @param name 서버의 표시 이름
     * @return 연결 결과
     */
    fun connect(server: ScriptedFtpServer, name: String = "server"): ConnectResult = runBlocking { manager.connect(itemOf(server, name)) }

    override fun close() {
        runBlocking { manager.disconnect() }
        scope.cancel()
        clients.forEach { runCatching { it.disconnect() } }
        servers.forEach { it.close() }
    }
}

/**
 * [condition]이 참이 될 때까지 기다린다. 검증 대상이 회귀해 조건이 끝내 참이 되지 않아도 테스트가 멈추지 않도록,
 * 기한([ScriptedFtpServer.TIMEOUT_MILLIS])이 지나면 실패로 끝낸다.
 * @param description 실패 메시지에 담을, 기다린 조건의 설명(예: "소켓이 닫히지")
 * @param condition 기다릴 조건
 */
internal fun awaitUntil(description: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ScriptedFtpServer.TIMEOUT_MILLIS.toLong())
    while (!condition()) {
        if (System.nanoTime() > deadline) fail("기한 안에 $description 않았다")
        Thread.sleep(5)
    }
}

/**
 * [client]로 탐색 세션에 접속한 관리자를 만든다. 가짜 클라이언트는 접속·로그인을 바로 받아들인다([FakeFtpClient]).
 * Keep-Alive는 진행하지 않는 테스트 스코프에서 돌아 명령을 보내지 않는다.
 * @param client 탐색 세션이 쓸 가짜 클라이언트
 * @return 연결된 관리자
 */
internal fun managerConnectedWith(client: FakeFtpClient): FtpSessionManager =
    FtpSessionManager(ConnectionLossNotices(), TestScope(), { client }).also { manager ->
        val server = RemoteStorageItem(name = "server", address = "host", port = 21, userId = "user", password = "password", settings = RemoteProtocolSettings.Ftp("UTF-8"))
        check(runBlocking { manager.connect(server) } == ConnectResult.Connected)
    }

/**
 * 서버에 접속하지 않은 관리자를 만든다. 경로 규칙만 쓰는 테스트가 FTP 파일 시스템을 만들 때 쓴다.
 * @return 탐색 세션이 없는 관리자
 */
internal fun unconnectedManager(): FtpSessionManager = FtpSessionManager(ConnectionLossNotices(), TestScope(), { FakeFtpClient() })
