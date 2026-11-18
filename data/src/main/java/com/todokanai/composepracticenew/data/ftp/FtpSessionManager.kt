package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.ConnectFailureReason
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RemoteStep
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.repository.ConnectionLossNoticeReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 세션들을 열고 닫으며, 화면이 보는 연결 상태를 정한다(#436).
 * - 탐색 세션: 사용자가 접속한 서버의 세션 하나. 목록 조회·이름 변경·새 폴더가 쓰고, 연결 상태([connectionState])는 이 세션에서 나온다.
 * - 작업 세션: 진행 표시가 있는 원격 작업(다운로드·업로드·삭제)이 작업마다 따로 여는 세션([withOperationSession]). 작업이 끝나면 닫히며,
 *   끊겨도 연결 상태·끊김 알림에 나오지 않고 그 작업의 실패 결과로만 알려진다.
 * 세션은 접속 한 번만 살므로([FtpSession]) 접속할 때마다 새 탐색 세션을 만든다. 접속은 접속끼리만 차례로 하고, 이전 탐색 세션이나 작업 세션의 I/O를 기다리지 않는다.
 * 잠금 규칙: 탐색 세션·연결 상태·작업 중 표시의 갱신은 stateMutex로 직렬화한다. 이 잠금을 쥔 채 세션의 잠금을 기다리지 않는다
 * (세션은 끊김을 알릴 때 자기 잠금을 놓은 뒤 이 잠금을 얻는다).
 * @param lossNotices 연결된 탐색 세션이 끊김으로 바뀔 때 끊김을 넣고, 새 연결을 시작할 때 알리지 않은 끊김을 빼는 알림함
 * @param scope 탐색 세션의 Keep-Alive와 바꾼 탐색 세션의 종료를 실행할 앱 수명 스코프
 * @param newClient 세션마다 새 FTP 클라이언트를 만든다. 앱은 실제 클라이언트를, 테스트는 가짜를 만든다
 * @param ioTimeoutMillis 세션의 소켓 읽기·쓰기 한 번의 상한(ms). [FtpSession]에 그대로 넘긴다
 * @param keepAliveIntervalMillis 세션마다 NOOP을 보내는 간격(ms)
 */
@Singleton
internal class FtpSessionManager(
    private val lossNotices: ConnectionLossNoticeReporter,
    private val scope: CoroutineScope,
    private val newClient: () -> SessionFtpClient,
    private val ioTimeoutMillis: Int = FtpSession.IO_TIMEOUT_MILLIS,
    private val keepAliveIntervalMillis: Long = KEEPALIVE_INTERVAL_MS
) {

    /** 앱의 실제 FTP 클라이언트로 세션을 여는 관리자를 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor(lossNotices: ConnectionLossNoticeReporter, @ApplicationScope scope: CoroutineScope) :
        this(lossNotices, scope, ::SessionFtpClient)

    /**
     * 탐색 세션과 그 표지·부품·Keep-Alive.
     * @param token 이 세션의 표지. 연결에 성공하면 [current]로 내보낸다
     * @param session 탐색 세션
     */
    private class Browsing(val token: RemoteSession, val session: FtpSession) {
        /** 이 세션에만 명령을 보내는 명령 부품. */
        val parts = FtpCommandParts(session)
        /** 연결에 성공한 뒤 도는 Keep-Alive. 연결 전이면 null. stateMutex로 보호한다. */
        var keepAlive: Job? = null

        /** Keep-Alive를 멈추고 세션을 닫는다. 진행 중 I/O를 기다리지 않는다([FtpSession.close]). */
        suspend fun close() {
            keepAlive?.cancel()
            session.close()
        }
    }

    /** 접속을 접속끼리만 차례로 하게 한다. 종료·끊김 종료는 이 잠금을 기다리지 않는다 — 진행 중인 접속도 끝낼 수 있어야 한다. */
    private val connectMutex = Mutex()
    /** [browsing]·[connected]·연결 상태·[busyCount] 갱신을 직렬화한다. */
    private val stateMutex = Mutex()
    /** 연결 중이거나 연결된(끊긴 것 포함) 탐색 세션. 없으면 null. stateMutex 안에서 바꾸고, 명령을 보내는 쪽은 잠금 없이 읽는다. */
    @Volatile private var browsing: Browsing? = null
    /** 연결된 탐색 세션의 표지. 연결 상태가 [RemoteConnectionState.Connected]일 때만 null이 아니다. stateMutex 안에서 바꾸고 잠금 없이 읽는다. */
    @Volatile private var connected: RemoteSession? = null
    /** 진행 중인 접속·종료 작업의 수. stateMutex로 보호한다. */
    private var busyCount = 0
    /** 마지막으로 발급한 세션 표지 id. */
    private val lastSessionId = AtomicLong()

    private val _connectionState = MutableStateFlow<RemoteConnectionState>(RemoteConnectionState.Disconnected)
    /**
     * 탐색 세션의 연결 상태. 예기치 않은 끊김과 앱이 요청한 종료를 구분해, 끊김은 다음 연결 시도([connect])나 종료 요청까지 [RemoteConnectionState.Lost]로 남긴다.
     * 작업 세션은 이 상태에 나오지 않는다.
     */
    val connectionState: StateFlow<RemoteConnectionState> = _connectionState.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    /** 접속·종료 작업이 하나라도 진행 중이면 true를 방출한다. */
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /**
     * 연결된 탐색 세션의 표지를 돌려준다. 잠금 없이 읽는다.
     * @return 연결된 세션의 표지. 연결돼 있지 않으면(연결 작업 중·끊김·종료됨) null
     */
    fun current(): RemoteSession? = connected

    /**
     * [server]에 새 탐색 세션으로 접속한다. 이전 탐색 세션은 닫되, 그 종료(진행 중 명령의 정리·QUIT)를 기다리지 않는다. 작업 세션은 건드리지 않는다.
     * 연결 상태는 시작할 때 [RemoteConnectionState.Disconnected]로 두고, 아직 화면에 알리지 않은 이전 세션의 끊김은 알림함에서 뺀다 — 새 연결이 끊긴 것으로 보이지 않도록.
     * 접속하는 동안 종료([disconnect])가 요청되면 어느 단계에서 끝났든 실패로 알린다.
     * @param server 접속할 서버
     * @return 연결 결과. 로그인했어도 서버의 목록을 해석할 수 없으면 연결을 닫고 실패를 돌려준다
     */
    suspend fun connect(server: RemoteStorageItem): ConnectResult = connectMutex.withLock {
        val token = RemoteSession(lastSessionId.incrementAndGet(), server)
        val mine = Browsing(token, newSession(onLost = { markBrowsingLost(token) }))
        val previous = stateMutex.withLock {
            beginBusy()
            lossNotices.connectionStarted()
            replaceBrowsing(mine, RemoteConnectionState.Disconnected)
        }
        var result: ConnectResult? = null
        try {
            previous?.let { scope.launch { it.close() } }
            settle(mine, mine.session.open(endpointOf(server))).also { result = it }
        } finally {
            withContext(NonCancellable) {
                // 지금 연결돼 있는지가 아니라 연결에 성공했는지로 판정한다 — 성공 직후 끊긴 세션(Lost)을 실패로 보고 치우면 종료 요청이 Lost를 지우지 못한다
                val abandoned = stateMutex.withLock {
                    endBusy()
                    (browsing === mine && result != ConnectResult.Connected).also { if (it) browsing = null }
                }
                // 실패했거나 취소된 접속의 세션은 탐색 세션으로 남기지 않고 닫는다 — 열린 뒤에 취소됐으면 로그인된 연결이 남지 않도록
                if (abandoned) mine.session.close()
            }
        }
    }

    /**
     * 연 결과를 연결 상태에 반영한다. 연 사이 종료나 다른 접속으로 바뀐 세션은 열렸어도 연결로 보지 않고 닫는다.
     * @param mine 연 탐색 세션
     * @param opening 연 결과
     * @return 연결 결과
     */
    private suspend fun settle(mine: Browsing, opening: SessionOpening): ConnectResult {
        val isConnected = stateMutex.withLock {
            val stillMine = opening == SessionOpening.Opened && browsing === mine
            if (stillMine) {
                connected = mine.token
                _connectionState.value = RemoteConnectionState.Connected(mine.token.server.name)
                mine.keepAlive = scope.launch { mine.session.keepAlive(keepAliveIntervalMillis) }
            }
            stillMine
        }
        if (isConnected) return ConnectResult.Connected
        if (opening == SessionOpening.Opened) mine.session.close()
        return ConnectResult.Failed(connectFailureReasonOf(opening))
    }

    /**
     * 연결로 삼지 못한 연 결과를 사용자에게 알릴 연결 실패 이유로 바꾼다.
     * 열렸지만 연결로 보지 않은 세션(연 사이 종료나 다른 접속으로 바뀜)은 다시 시도하면 될 수 있는 실패다.
     * @param opening 연결로 삼지 못한 연 결과
     * @return 연결 실패 이유
     */
    private fun connectFailureReasonOf(opening: SessionOpening): ConnectFailureReason = when (opening) {
        SessionOpening.ListingFormatUnknown -> ConnectFailureReason.LISTING_FORMAT_UNKNOWN
        SessionOpening.Opened, is SessionOpening.Refused, is SessionOpening.Unreachable, SessionOpening.Closed -> ConnectFailureReason.CONNECTION_FAILED
    }

    /**
     * 탐색 세션을 닫는다. 진행 중인 명령은 기다리지 않고 중단한다. 연결 작업 중이면 그 연결 작업은 어느 단계에서 끝나든 연결 성공이 되지 않는다(앱 종료 뒤에 연결이 남지 않도록).
     * 작업 세션은 닫지 않는다 — 진행 중인 원격 작업은 계속되고, 끝날 때 자기 세션을 닫는다.
     * 탐색 세션이 없으면 아무 동작도 하지 않는다.
     */
    suspend fun disconnect() {
        val previous = stateMutex.withLock {
            val current = browsing ?: return
            beginBusy()
            replaceBrowsing(null, RemoteConnectionState.Disconnected)
            current
        }
        closeWhileBusy(previous)
    }

    /**
     * 앱이 연결을 더 유지할 수 없어 연결된 탐색 세션을 [disconnect]와 같이 닫는다. 사용자가 요청한 종료가 아니므로 끊김으로 남기고 알림함에도 넣는다.
     * 연결돼 있는지를 종료 기록과 같은 잠금 안에서 확인한다 — 연결 작업 중인 세션(보호하던 연결이 아닌 새 연결)을 끝내거나, 이미 끊긴 세션의 끊김을 종료로 덮어쓰지 않도록.
     * 작업 세션은 닫지 않는다.
     * @return 연결된 세션을 끝냈으면 true, 연결된 세션이 없어 아무것도 하지 않았으면 false
     */
    suspend fun drop(): Boolean {
        val dropped = stateMutex.withLock {
            if (connected == null) return false
            beginBusy()
            lossNotices.report()
            replaceBrowsing(null, RemoteConnectionState.Lost)
        }
        closeWhileBusy(dropped)
        return true
    }

    /**
     * 탐색 세션의 명령 부품으로 [block]을 실행한다. 목록 조회가 쓴다 — 이전 목록 요청의 결과는 목록 요청 세대가 버리므로 세션 표지를 확인하지 않는다.
     * @param block 탐색 세션의 부품으로 실행할 명령
     * @return block의 결과. 탐색 세션이 없으면 [FailureReason.NotConnected]
     */
    suspend fun <T> withBrowsingSession(block: suspend (FtpCommandParts<FtpSession>) -> IoResult<T>): IoResult<T> {
        val current = browsing ?: return IoResult.ConnectionLost(FailureReason.NotConnected)
        return block(current.parts)
    }

    /**
     * [token]의 세션이 지금의 탐색 세션일 때만 그 명령 부품으로 [block]을 실행한다. 진행 표시가 없는 원격 작업(이름 변경·새 폴더)이 쓴다.
     * 그 사이 다른 서버에 접속했거나 종료됐으면 실행하지 않는다 — 고른 세션이 아닌 곳에 명령을 보내지 않도록.
     * @param token 작업을 요청할 때 묶은 세션 표지
     * @param block 탐색 세션의 부품으로 실행할 명령
     * @return block의 결과. 탐색 세션이 [token]의 세션이 아니면 [FailureReason.NotConnected]
     */
    suspend fun <T> withBrowsingSession(token: RemoteSession, block: suspend (FtpCommandParts<FtpSession>) -> IoResult<T>): IoResult<T> {
        val current = browsing?.takeIf { it.token.id == token.id } ?: return IoResult.ConnectionLost(FailureReason.NotConnected)
        return block(current.parts)
    }

    /**
     * [token]의 서버에 이 작업만의 세션을 열어 그 명령 부품으로 [block]을 실행하고, 끝나면(성공·실패·취소 모두) 세션을 닫는다.
     * 탐색 세션의 상태와 무관하다 — 그 사이 다른 서버에 접속했거나 탐색 세션이 끊겼어도 고른 서버로 간다.
     * 서버가 작업용 연결을 거부하면(접속 인사의 421, 로그인 거부) 그 응답을 담아 [IoResult.ConnectionLost]로 끝난다. 탐색 세션은 그대로다.
     * 지원 범위는 탐색 세션 하나와 진행 중인 작업 수만큼의 동시 접속을 허용하는 서버다.
     * 여는 동안 취소되면 세션의 소켓을 닫아 바로 끝낸다([FtpSession.open]). 작업 중에는 세션마다 Keep-Alive가 돈다.
     * @param token 작업을 요청할 때 묶은 세션 표지
     * @param block 작업 세션의 부품으로 실행할 작업
     * @return block의 결과, 또는 작업 세션을 열지 못한 실패
     */
    suspend fun <T> withOperationSession(token: RemoteSession, block: suspend (FtpCommandParts<FtpSession>) -> IoResult<T>): IoResult<T> {
        val session = newSession()
        try {
            return when (val opening = session.open(endpointOf(token.server))) {
                SessionOpening.Opened -> runKeepingAlive(session, block)
                is SessionOpening.Refused -> openFailure(FailureReason.ServerRefused(RemoteStep.OPEN_SESSION, opening.reply))
                SessionOpening.ListingFormatUnknown -> openFailure(FailureReason.RemoteStepFailed(RemoteStep.OPEN_SESSION, FailureReason.ListingFormatUnknown))
                is SessionOpening.Unreachable -> openFailure(FailureReason.RemoteStepFailed(RemoteStep.OPEN_SESSION, connectionFailureReason(opening.cause)))
                SessionOpening.Closed -> openFailure(FailureReason.RemoteStepFailed(RemoteStep.OPEN_SESSION, FailureReason.ConnectionClosed))
            }
        } finally {
            withContext(NonCancellable) { session.close() }
        }
    }

    /**
     * 연 작업 세션에서 Keep-Alive를 돌리며 [block]을 실행한다. Keep-Alive는 [block]이 끝나면 멈춘다.
     * @param session 연 작업 세션
     * @param block 작업 세션의 부품으로 실행할 작업
     * @return block의 결과
     */
    private suspend fun <T> runKeepingAlive(session: FtpSession, block: suspend (FtpCommandParts<FtpSession>) -> IoResult<T>): IoResult<T> = coroutineScope {
        val keepAlive = launch { session.keepAlive(keepAliveIntervalMillis) }
        try {
            block(FtpCommandParts(session))
        } finally {
            keepAlive.cancel()
        }
    }

    /**
     * 작업 세션을 열지 못한 실패를 결과로 만든다.
     * @param reason 열지 못한 원인
     * @return 연결 끊김 결과
     */
    private fun openFailure(reason: FailureReason): IoResult<Nothing> = IoResult.ConnectionLost(reason)

    /**
     * 연결된 탐색 세션이 끊겼으면 연결 상태를 끊김으로 바꾸고 같은 잠금 안에서 알림함에 넣는다 — 상태와 알림이 어긋나지 않도록.
     * 그 사이 다른 세션으로 바뀌었거나 종료·끊김 종료된 세션의 끊김은 무시한다.
     * @param token 끊긴 탐색 세션의 표지
     */
    private suspend fun markBrowsingLost(token: RemoteSession) = stateMutex.withLock {
        if (connected !== token) return@withLock
        connected = null
        _connectionState.value = RemoteConnectionState.Lost
        lossNotices.report()
    }

    /**
     * 탐색 세션을 [next]로 바꾸고 연결 상태를 [state]로 둔다. stateMutex를 쥔 상태에서 부른다.
     * @param next 새 탐색 세션. 없애면 null
     * @param state 바꾼 뒤의 연결 상태
     * @return 바뀌기 전의 탐색 세션
     */
    private fun replaceBrowsing(next: Browsing?, state: RemoteConnectionState): Browsing? {
        val previous = browsing
        browsing = next
        connected = null
        _connectionState.value = state
        return previous
    }

    /**
     * 종료 작업으로 [target]을 닫고, 다 닫으면 작업 중 표시를 거둔다.
     * @param target 닫을 탐색 세션. [beginBusy]를 부른 뒤에 넘긴다
     */
    private suspend fun closeWhileBusy(target: Browsing?) {
        try {
            target?.close()
        } finally {
            withContext(NonCancellable) { stateMutex.withLock { endBusy() } }
        }
    }

    /** 접속·종료 작업 하나를 시작했음을 기록한다. stateMutex를 쥔 상태에서 부른다. */
    private fun beginBusy() {
        busyCount++
        _isConnecting.value = true
    }

    /** 접속·종료 작업 하나가 끝났음을 기록한다. stateMutex를 쥔 상태에서 부른다. */
    private fun endBusy() {
        busyCount--
        _isConnecting.value = busyCount > 0
    }

    /**
     * 새 FTP 클라이언트를 소유하는 세션을 만든다.
     * @param onLost 로그인된 세션이 예기치 않게 끊겼을 때 부를 동작
     * @return 아직 열지 않은 세션
     */
    private fun newSession(onLost: suspend () -> Unit = {}): FtpSession = FtpSession(newClient(), ioTimeoutMillis, onLost)

    /**
     * 저장된 서버의 접속 정보를 FTP 세션의 접속 정보로 바꾼다.
     * @param server 저장된 원격 서버
     * @return FTP 접속 정보
     */
    private fun endpointOf(server: RemoteStorageItem): FtpEndpoint = when (val settings = server.settings) {
        is RemoteProtocolSettings.Ftp -> FtpEndpoint(server.address, server.port, server.userId, server.password, settings.encoding)
    }
}
