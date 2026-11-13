package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.Closeable
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 단일 FTPClient와 그 잠금·로그인 상태를 캡슐화하는 FTP 세션.
 * 클라이언트와 Mutex는 외부로 노출하지 않으며, 명령 실행은 [withClient] 또는 [withConnectedClient]를 통해서만 가능하다.
 * 쓰는 쪽은 자기 역할의 인터페이스로만 의존한다 — 명령은 [FtpCommandSession], 데이터 연결은 [FtpDataCommandSession], 연결 수명은 [FtpConnectionLifecycle], 상태 읽기는 [FtpConnectionStatus].
 * 잠금 규칙: 로그인 상태 플래그는 stateMutex, FTP 명령 I/O는 ioMutex로 직렬화한다.
 * 서버가 응답하지 않아도 명령이 잠금을 쥔 채 끝없이 기다리지 않도록, [client]의 모든 소켓 읽기·쓰기 한 번에 [ioTimeoutMillis]의 상한을 건다.
 * 상한은 [client]가 소켓 팩토리로 만드는 제어 연결과 수동 모드 데이터 연결에 걸린다. 능동 모드 데이터 연결은 상한을 걸 수 없어 열지 않는다([PassiveOnlyServerSocketFactory]).
 * 상한을 넘긴 I/O는 IOException으로 끝나며, 그 의미(이 항목만의 실패인지 세션 종료인지)는 다른 I/O 실패와 같이 정한다([withConnectedClient]).
 * @param client 세션이 단독 소유하는 FTP 클라이언트. 외부에는 [withClient]·[withConnectedClient]로만 노출한다. 앱에서는 새 클라이언트를, 테스트에서는 가짜 클라이언트를 넘긴다.
 * 목록 조회도 데이터 스트림을 직접 열어야 하므로([DataCommandRunner]) 그 함수를 가진 [SessionFtpClient]다.
 * 소켓 상한·소켓 팩토리는 세션이 정하므로 먼저 정해 둔 값은 덮어쓴다 — 상한을 바꾸려면 [ioTimeoutMillis]로 넘긴다
 * @param lossNotices 연결된 세션이 끊김으로 바뀔 때 끊김을 넣고, 새 연결을 시작할 때 알리지 않은 끊김을 빼는 알림함
 * @param ioTimeoutMillis 소켓 읽기·쓰기 한 번에서 데이터가 하나도 오가지 않을 때 기다리는 상한(ms). 전송 전체 시간의 상한이 아니다. 0보다 커야 한다
 */
@Singleton
internal class FtpSession(
    private val client: SessionFtpClient,
    private val lossNotices: ConnectionLossNotices,
    ioTimeoutMillis: Int = IO_TIMEOUT_MILLIS
) : FtpCommandSession, FtpDataCommandSession, FtpConnectionLifecycle {

    /** 새 FTPClient를 소유하는 세션을 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor(lossNotices: ConnectionLossNotices) : this(SessionFtpClient(), lossNotices)

    init {
        // 0 이하는 상한이 아니다 — SO_TIMEOUT 0은 무제한 읽기, 쓰기 감시는 즉시 닫기, 음수는 setSoTimeout이 거부한다
        require(ioTimeoutMillis > 0) { "ioTimeoutMillis must be positive: $ioTimeoutMillis" }
    }

    /** isLoggedIn, _connectionState, _isConnecting 갱신을 직렬화한다. */
    private val stateMutex = Mutex()
    /** FTP 명령 I/O를 직렬화한다. [closeBypassingIoLock]은 이 lock을 기다리지 않고 진행 중 I/O의 소켓을 닫아 즉시 중단한다. */
    private val ioMutex = Mutex()
    /** 로그인까지 완료된 상태인지 여부. stateMutex로 보호한다. */
    private var isLoggedIn = false
    /** [closeBypassingIoLock]이 요청된 횟수. stateMutex 안에서 늘리고, ioMutex를 쥔 연결 작업이 잠금 없이 읽으므로 원자 값으로 둔다. */
    private val bypassCloseRequests = AtomicInteger()
    /** [client]가 연 제어·데이터 소켓. [closeBypassingIoLock]이 잠금 밖에서 FTPClient를 건드리지 않고 진행 중 I/O를 깨우려고 닫는다. */
    private val sockets = AbortableSocketFactory(writeTimeoutMillis = ioTimeoutMillis.toLong()).also { client.setSocketFactory(it) }

    init {
        // 쓰기 상한은 [sockets]가 만든 소켓이 건다. 읽기 상한은 FTPClient가 소켓을 열 때 SO_TIMEOUT으로 건다 — 제어 연결은 접속할 때(defaultTimeout), 데이터 연결은 열 때(dataTimeout)
        client.defaultTimeout = ioTimeoutMillis
        client.setDataTimeout(Duration.ofMillis(ioTimeoutMillis.toLong()))
        // 능동 모드 데이터 소켓은 서버 소켓의 accept로 만들어져 [sockets]를 거치지 않는다 — 상한 없이 막히지 않도록 열지 못하게 한다
        client.setServerSocketFactory(PassiveOnlyServerSocketFactory())
    }

    private val _connectionState = MutableStateFlow<RemoteConnectionState>(RemoteConnectionState.Disconnected)
    /**
     * FTP 서버와의 연결 상태. 예기치 않은 끊김([markConnectionDropped])과 앱이 요청한 종료([markLoggedOut]·[closeBypassingIoLock])를 구분해,
     * 끊김은 다음 연결 시도([beginConnecting])나 종료 요청까지 [RemoteConnectionState.Lost]로 남긴다.
     */
    override val connectionState: StateFlow<RemoteConnectionState> = _connectionState.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    /** connect() 또는 disconnect() 호출이 진행 중인 경우 true를 방출한다. */
    override val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /** 소켓 연결과 로그인이 모두 유효하면 true를 반환한다. */
    override suspend fun isReady(): Boolean = stateMutex.withLock { client.isConnected && isLoggedIn }

    /** 소켓이 열려 있으면 true를 반환한다. 잠금 없이 조회한다. */
    override fun isSocketOpen(): Boolean = client.isConnected

    /**
     * ioMutex를 확보한 상태로 [block]에 FTPClient를 넘겨 실행한다. 스레드 전환은 호출부가 결정한다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 반환값
     */
    override suspend fun <T> withClient(block: suspend (SessionFtpClient) -> T): T = ioMutex.withLock { block(client) }

    /**
     * 연결된 상태에서만 [withClient]로 [block]을 실행한다. FTP 명령 실패의 의미를 정하는 경계이며, 규칙은 다음 하나뿐이다.
     * - [block]이 돌려준 결과: 서버 거부 응답·데이터 소켓 연결([openDataConnection])·데이터 스트림·로컬 파일 실패처럼 제어 채널의 응답 순서가 유지된 결과다. 그대로 돌려준다
     * - [block]이 던진 IOException: 서버가 연결을 닫았거나([FTPConnectionClosedException]) 응답을 읽다 실패(타임아웃 등)한 것이다.
     *   늦게 온 응답이 다음 명령의 응답으로 읽혀 순서가 어긋날 수 있고 commons-net에는 이를 다시 맞출 방법이 없으므로, 세션을 종료하고 [IoResult.ConnectionLost]를 돌려준다
     * - 그 밖의 예외(구현 오류): 명령이 어디까지 진행됐는지 알 수 없어 응답 순서를 보장할 수 없으므로, 세션을 종료하고 그대로 전파한다
     * 그래서 [block] 안에서 세션을 유지할 실패는 예외로 던지지 말고 결과로 돌려줘야 한다.
     * IOException의 출처는 구분할 수 없으므로 로컬 파일 I/O는 [block] 밖에서 하거나, [DataCommandRunner.run]의 exchange에서만 한다(그 안의 실패는 데이터 연결 실패로 결과가 된다).
     * 잠금을 얻은 시점에 미연결이면 실행하지 않고 [IoResult.ConnectionLost]를 돌려준다. 취소는 그대로 전파한다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 결과, 또는 연결 끊김
     */
    override suspend fun <T> withConnectedClient(block: suspend (FtpCommandClient) -> IoResult<T>): IoResult<T> = withClient { client ->
        if (!isReady()) return@withClient IoResult.ConnectionLost(NOT_CONNECTED_MESSAGE)
        try {
            block(client)
        } catch (e: IOException) {
            endUnusableSession()
            IoResult.ConnectionLost(unusableSessionMessage(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            endUnusableSession()
            throw e
        }
    }

    /**
     * [open]으로 데이터 연결을 쓰는 명령의 데이터 스트림을 열어, 정상 끝과 중단을 구분해 닫을 수 있는 [DataConnection]으로 돌려준다.
     * ioMutex를 쥔 [withConnectedClient]의 블록 안에서 호출한다. 명령의 실행 규칙은 [DataCommandRunner]가 정한다.
     * 데이터 소켓을 연결하지 못한 실패만 결과([DataConnectionOpening.NotConnected])로 돌려준다 — 그 실패는 명령을 보내기 전이라 응답 순서가 유지된다(#267).
     * 데이터 소켓을 연결한 뒤의 실패(REST·명령의 응답, 원격 주소 확인)는 명령의 응답이 남았을 수 있으므로 예외로 전파한다.
     * @param open 데이터 스트림을 여는 명령(`retrieveFileStream`·`storeFileStream`, [SessionFtpClient]의 목록 조회). 서버가 거부하면 null을 돌려준다
     * @return 열린 데이터 연결, 서버의 거부, 또는 데이터 소켓을 연결하지 못함
     * @throws IOException 명령의 응답을 읽지 못했거나 데이터 소켓을 연결한 뒤에 실패한 경우
     */
    override fun <S : Closeable> openDataConnection(open: () -> S?): DataConnectionOpening<S> {
        val opened = mutableListOf<WriteBoundedSocket>()
        val stream = try {
            sockets.capturing(opened, open)
        } catch (e: IOException) {
            if (!isDataSocketUnconnected(opened)) throw e
            // commons-net은 연결하지 못한 데이터 소켓을 닫지 않는다
            opened.forEach { runCatching { it.close() } }
            return DataConnectionOpening.NotConnected(e)
        }
        return if (stream == null) DataConnectionOpening.Refused else DataConnectionOpening.Opened(DataConnection(stream, opened))
    }

    /**
     * 데이터 스트림을 열다 난 실패가 데이터 소켓을 연결하기 전의 것인지 판별한다. 소켓을 만들기 전의 실패(PASV 응답을 읽지 못함)는 해당하지 않는다.
     * 한 번 연결된 소켓은 닫혀도 연결된 것으로 남으므로([java.net.Socket.isConnected]), 연결한 뒤의 실패와 섞이지 않는다.
     * @param opened 실패한 명령이 그때까지 만든 데이터 소켓
     * @return 데이터 소켓을 만들었으나 하나도 연결하지 못했으면 true
     */
    private fun isDataSocketUnconnected(opened: List<WriteBoundedSocket>): Boolean =
        opened.isNotEmpty() && opened.none { it.isConnected }

    /**
     * 응답 순서를 알 수 없게 된 세션을 끊김 상태로 바꾸고, 그 제어 채널을 다시 쓰지 않도록 소켓을 닫는다.
     * 예외로 전파할 수 없는 경로(취소 정리 중 응답을 읽지 못한 경우 등)에서도 호출한다. ioMutex를 쥔 상태에서 호출한다.
     * 취소된 코루틴에서도 끝까지 실행한다 — 상태 잠금을 기다리다 취소로 멈추면 쓸 수 없는 세션이 로그인 상태로 남는다.
     */
    override suspend fun endUnusableSession() {
        withContext(NonCancellable) {
            markConnectionDropped()
            runCatching { client.disconnect() }
        }
    }

    /**
     * 세션을 종료하게 된 실패를 사용자에게 보여줄 메시지로 바꾼다.
     * @param cause 세션을 종료하게 된 예외
     * @return 표시할 메시지
     */
    private fun unusableSessionMessage(cause: IOException): String =
        if (cause is FTPConnectionClosedException) CONNECTION_LOST_MESSAGE
        else "$NO_RESPONSE_MESSAGE (${cause.message ?: cause.javaClass.simpleName})"

    /**
     * FTP 연결이 서버 측에서 끊긴 경우 로그인 상태를 초기화한다. 진행 중인 작업은 다음 명령에서 [withConnectedClient]가 끊김으로 알린다.
     * 연결된 세션에서만 끊김([RemoteConnectionState.Lost])으로 기록한다 — 앱이 먼저 종료한 세션의 진행 중 명령이 소켓이 닫혀 실패한 것은 끊김이 아니다.
     * 끊김으로 기록할 때 같은 잠금 안에서 알림함([lossNotices])에도 넣는다 — 상태와 알림이 어긋나지 않도록.
     */
    suspend fun markConnectionDropped() {
        stateMutex.withLock {
            isLoggedIn = false
            if (_connectionState.value is RemoteConnectionState.Connected) {
                _connectionState.value = RemoteConnectionState.Lost
                lossNotices.report()
            }
        }
    }

    /**
     * 새 연결 작업을 시작한다. 연결 상태는 이전 세션이 연결돼 있었든 끊겼든 [RemoteConnectionState.Disconnected]로 둔다 —
     * 연결 작업이 ioMutex를 기다리는 동안 이전 세션의 명령이 실패해도([markConnectionDropped]) 그건 새 연결의 끊김이 아니므로 [RemoteConnectionState.Lost]가 되지 않는다.
     * 아직 화면에 알리지 않은 이전 세션의 끊김은 알림함([lossNotices])에서 뺀다 — 새 연결이 끊긴 것으로 보이지 않도록.
     * 종료 요청 표지를 연결 작업 표시와 같은 잠금 안에서 읽는다 — 연결 작업으로 보인 뒤에 온 종료 요청([closeBypassingIoLock])은 모두 [markLoggedIn]이 알아챈다.
     * @return 연결 작업이 [markLoggedIn]에 넘길 종료 요청 표지
     */
    override suspend fun beginConnecting(): Int = stateMutex.withLock {
        _isConnecting.value = true
        _connectionState.value = RemoteConnectionState.Disconnected
        lossNotices.connectionStarted()
        bypassCloseRequests.get()
    }

    /** 연결 작업이 끝났음을 기록한다. */
    override suspend fun endConnecting() = stateMutex.withLock { _isConnecting.value = false }

    /**
     * 로그인 성공 상태로 전환한다. 새 연결의 현재 디렉터리는 로그인 직후의 홈이다.
     * [closeMark]를 읽은 뒤 종료가 요청됐으면 전환하지 않는다 — 종료 요청과 같은 잠금 안에서 판단하므로, 연결 작업과 겹친 종료 요청이 연결 상태로 뒤집히지 않는다.
     * @param closeMark 연결 작업을 시작할 때 읽은 종료 요청 표지([closeRequestMark])
     * @param home 로그인 직후의 현재 디렉터리(FTP 경로). 서버가 알려주지 않았으면 null
     * @param serverName 연결한 서버의 표시 이름. 연결 상태([RemoteConnectionState.Connected])에 함께 담는다
     * @return 로그인 상태로 전환했으면 true, 그 사이 종료가 요청돼 전환하지 않았으면 false
     */
    override suspend fun markLoggedIn(closeMark: Int, home: String?, serverName: String): Boolean = stateMutex.withLock {
        if (isCloseRequestedSince(closeMark)) return@withLock false
        client.recordLogin(home)
        isLoggedIn = true
        _connectionState.value = RemoteConnectionState.Connected(serverName)
        true
    }

    /**
     * 지금까지 [closeBypassingIoLock]이 요청된 횟수를 돌려준다. 연결 작업이 시작할 때 읽어 두었다가 [markLoggedIn]으로 그 사이의 종료 요청을 확인한다.
     * @return 종료 요청 표지
     */
    internal fun closeRequestMark(): Int = bypassCloseRequests.get()

    /**
     * [mark]를 읽은 뒤 [closeBypassingIoLock]이 요청됐는지 판별한다.
     * @param mark [closeRequestMark]로 읽어 둔 표지
     * @return 그 뒤 종료가 요청됐으면 true
     */
    private fun isCloseRequestedSince(mark: Int): Boolean = bypassCloseRequests.get() != mark

    /** 로그인 해제 상태로 전환한다. */
    override suspend fun markLoggedOut() = stateMutex.withLock {
        isLoggedIn = false
        _connectionState.value = RemoteConnectionState.Disconnected
    }

    /**
     * 진행 중인 FTP 명령을 기다리지 않고 연결을 끊는다. 로그인 상태를 먼저 해제하므로 [withConnectedClient]는 이를 연결 끊김으로 알린다.
     * 진행 중인 명령이 없으면 ioMutex를 얻어 로그아웃한 뒤 연결을 닫는다.
     * 진행 중이면 그 명령이 쓰는 소켓(제어·데이터)만 중단으로 닫는다(업로드가 서버에 파일 끝으로 읽히지 않도록) — FTPClient는 스레드에 안전하지 않으므로 잠금 밖에서 명령을 보내거나 내부 상태를 바꾸지 않는다.
     * 그 명령은 IOException을 받아 자기 잠금 안에서 세션을 정리하고, 이 함수는 ioMutex를 얻은 뒤 남은 연결을 닫는다.
     * 진행 중인 연결 작업이 이 종료를 연결 결과에 반영하도록([markLoggedIn]), 소켓을 닫기 전에 요청을 기록한다.
     * 호출 전 소켓이 열려 있거나([isSocketOpen]) 연결 작업 중인지([isConnecting]) 확인해야 한다.
     */
    override suspend fun closeBypassingIoLock() {
        stateMutex.withLock { markClosing(RemoteConnectionState.Disconnected) }
        closeClient()
    }

    /**
     * 연결된 세션만 [closeBypassingIoLock]과 같이 닫되, 사용자가 요청한 종료가 아니므로 끊김([RemoteConnectionState.Lost])으로 남기고 알림함([lossNotices])에도 넣는다.
     * 연결돼 있는지를 종료 기록과 같은 잠금 안에서 확인한다 — 연결 작업 중인 세션(보호하던 연결이 아닌 새 연결)을 끝내거나, 이미 끊긴 세션의 끊김을 종료로 덮어쓰지 않도록.
     * @return 연결된 세션을 끝냈으면 true, 연결된 세션이 없어 아무것도 하지 않았으면 false
     */
    override suspend fun dropConnected(): Boolean {
        val connected = stateMutex.withLock {
            val isConnected = _connectionState.value is RemoteConnectionState.Connected
            if (isConnected) {
                markClosing(RemoteConnectionState.Lost)
                lossNotices.report()
            }
            isConnected
        }
        if (connected) closeClient()
        return connected
    }

    /**
     * 잠금 밖 종료를 기록하고 연결 상태를 [state]로 둔다. stateMutex를 쥔 상태에서 호출한다.
     * 진행 중인 연결 작업이 이 종료를 연결 결과에 반영하도록([markLoggedIn]), 소켓을 닫기 전에 요청을 기록한다.
     * @param state 종료 뒤의 연결 상태
     */
    private fun markClosing(state: RemoteConnectionState) {
        bypassCloseRequests.incrementAndGet()
        isLoggedIn = false
        _connectionState.value = state
        _isConnecting.value = true
    }

    /** [markClosing]으로 기록한 종료를 진행 중 I/O를 기다리지 않고 실행한다 — 명령이 없으면 로그아웃 후 닫고, 있으면 소켓만 중단으로 닫는다. */
    private suspend fun closeClient() {
        if (ioMutex.tryLock()) {
            try {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            } finally {
                ioMutex.unlock()
            }
        } else {
            sockets.closeAll()
            ioMutex.withLock { runCatching { client.disconnect() } }
        }
        stateMutex.withLock { _isConnecting.value = false }
    }

    companion object {
        /** 소켓 읽기·쓰기 한 번을 기다리는 기본 상한(ms). FileZilla의 기본 무응답 제한(20초)과 비슷한 수준이다. */
        private const val IO_TIMEOUT_MILLIS = 15_000
        /** 연결되지 않은 상태에서 명령을 실행하려 할 때 표시할 메시지. */
        private const val NOT_CONNECTED_MESSAGE = "FTP 서버에 연결되어 있지 않습니다"
        /** 서버가 연결을 닫았을 때 표시할 메시지. */
        private const val CONNECTION_LOST_MESSAGE = "FTP 서버와의 연결이 끊겼습니다"
        /** 서버 응답을 받지 못해 세션을 종료했을 때 표시할 메시지. */
        private const val NO_RESPONSE_MESSAGE = "FTP 서버 응답을 받지 못해 연결을 종료했습니다"
    }
}
