package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPReply
import java.io.Closeable
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 접속 한 번(접속 → 로그인 → 종료)만 사는 FTP 세션. 단일 FTPClient와 그 잠금·로그인 상태를 캡슐화한다.
 * 한 번 연 세션은 다시 접속하지 않는다 — 닫히거나 끊긴 세션은 다시 쓰지 않고, 새 접속은 새 세션 객체가 맡는다(#436).
 * 그래서 이 세션에 보낸 명령은 이 세션의 서버에만 가며, 명령을 보내는 쪽이 다른 접속으로 이어질 수 없다.
 * 클라이언트와 Mutex는 외부로 노출하지 않으며, 명령 실행은 [withClient] 또는 [withConnectedClient]를 통해서만 가능하다.
 * 쓰는 쪽은 자기 역할의 인터페이스로만 의존한다 — 명령은 [FtpCommandSession], 데이터 연결은 [FtpDataCommandSession].
 * 어느 세션을 열고 닫을지, 연결 상태를 어떻게 보일지는 [FtpSessionManager]가 정한다.
 * 잠금 규칙: 로그인·종료 상태 플래그는 stateMutex, FTP 명령 I/O는 ioMutex로 직렬화한다.
 * 서버가 응답하지 않아도 명령이 잠금을 쥔 채 끝없이 기다리지 않도록, [client]의 모든 소켓 읽기·쓰기 한 번에 [ioTimeoutMillis]의 상한을 건다.
 * 상한은 [client]가 소켓 팩토리로 만드는 제어 연결과 수동 모드 데이터 연결에 걸린다. 능동 모드 데이터 연결은 상한을 걸 수 없어 열지 않는다([PassiveOnlyServerSocketFactory]).
 * 상한을 넘긴 I/O는 IOException으로 끝나며, 그 의미(이 항목만의 실패인지 세션 종료인지)는 다른 I/O 실패와 같이 정한다([withConnectedClient]).
 * @param client 세션이 단독 소유하는 FTP 클라이언트. 외부에는 [withClient]·[withConnectedClient]로만 노출한다. 앱에서는 새 클라이언트를, 테스트에서는 가짜 클라이언트를 넘긴다.
 * 목록 조회도 데이터 스트림을 직접 열어야 하므로([DataCommandRunner]) 그 함수를 가진 [SessionFtpClient]다.
 * 소켓 상한·소켓 팩토리는 세션이 정하므로 먼저 정해 둔 값은 덮어쓴다 — 상한을 바꾸려면 [ioTimeoutMillis]로 넘긴다
 * @param ioTimeoutMillis 소켓 읽기·쓰기 한 번에서 데이터가 하나도 오가지 않을 때 기다리는 상한(ms). 전송 전체 시간의 상한이 아니다. 0보다 커야 한다
 * @param onLost 로그인된 세션이 예기치 않게 끊겼을 때(응답 순서를 잃음·서버가 닫음) 한 번 부른다. 앱이 요청한 종료([close])에서는 부르지 않는다.
 * 끊김을 연결 상태에 보일지는 받는 쪽이 정한다. 취소되지 않는 문맥에서 부른다
 */
internal class FtpSession(
    private val client: SessionFtpClient,
    ioTimeoutMillis: Int = IO_TIMEOUT_MILLIS,
    private val onLost: suspend () -> Unit = {}
) : FtpCommandSession, FtpDataCommandSession {

    init {
        // 0 이하는 상한이 아니다 — SO_TIMEOUT 0은 무제한 읽기, 쓰기 감시는 즉시 닫기, 음수는 setSoTimeout이 거부한다
        require(ioTimeoutMillis > 0) { "ioTimeoutMillis must be positive: $ioTimeoutMillis" }
    }

    /** isLoggedIn, isClosed 갱신을 직렬화한다. */
    private val stateMutex = Mutex()
    /** FTP 명령 I/O를 직렬화한다. [close]는 이 lock을 기다리지 않고 진행 중 I/O의 소켓을 닫아 즉시 중단한다. */
    private val ioMutex = Mutex()
    /** 로그인까지 완료된 상태인지 여부. stateMutex로 보호한다. */
    private var isLoggedIn = false
    /** [close]가 요청됐는지 여부. 한 번 참이 되면 다시 거짓이 되지 않으며, 그 뒤로는 로그인 상태가 되지 않는다. stateMutex로 보호한다. */
    private var isClosed = false
    /** [open]을 이미 불렀는지 여부. 세션은 한 번만 연다. */
    private val openRequested = AtomicBoolean(false)
    /** [client]가 연 제어·데이터 소켓. [close]가 잠금 밖에서 FTPClient를 건드리지 않고 진행 중 I/O를 깨우려고 닫는다. */
    private val sockets = AbortableSocketFactory(writeTimeoutMillis = ioTimeoutMillis.toLong()).also { client.setSocketFactory(it) }

    init {
        // 쓰기 상한은 [sockets]가 만든 소켓이 건다. 읽기 상한은 FTPClient가 소켓을 열 때 SO_TIMEOUT으로 건다 — 제어 연결은 접속할 때(defaultTimeout), 데이터 연결은 열 때(dataTimeout)
        client.defaultTimeout = ioTimeoutMillis
        client.setDataTimeout(Duration.ofMillis(ioTimeoutMillis.toLong()))
        // 능동 모드 데이터 소켓은 서버 소켓의 accept로 만들어져 [sockets]를 거치지 않는다 — 상한 없이 막히지 않도록 열지 못하게 한다
        client.setServerSocketFactory(PassiveOnlyServerSocketFactory())
    }

    /** 소켓 연결과 로그인이 모두 유효하면 true를 반환한다. */
    override suspend fun isReady(): Boolean = stateMutex.withLock { client.isConnected && isLoggedIn }

    /**
     * ioMutex를 확보한 상태로 [block]에 FTPClient를 넘겨 실행한다. 스레드 전환은 호출부가 결정한다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 반환값
     */
    suspend fun <T> withClient(block: suspend (SessionFtpClient) -> T): T = ioMutex.withLock { block(client) }

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
        if (!isReady()) return@withClient IoResult.ConnectionLost(FailureReason.NotConnected)
        try {
            block(client)
        } catch (e: IOException) {
            endUnusableSession()
            IoResult.ConnectionLost(connectionFailureReason(e))
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
     * [endpoint]의 서버에 접속하고 로그인해, 명령을 보낼 수 있는 세션으로 연다. 세션마다 한 번만 부를 수 있다.
     * 로그인한 뒤 목록을 해석할 수 없는 서버([FtpListingFormat.isReadable])면 어느 폴더도 볼 수 없으므로 열지 않는다.
     * 로그인 직후의 현재 디렉터리(홈)를 기록한다 — 연결 확인은 디렉터리를 옮기지 않으므로 그때의 현재 디렉터리가 홈이다. 서버가 PWD를 거부하면 홈을 모르는 채로 연다.
     * 여는 동안 종료가 요청됐으면([close]) 어느 단계에서 끝났든 열린 것으로 보지 않는다. 열지 못하면 소켓을 닫아 연결을 남기지 않는다.
     * 접속·응답 읽기는 막히는 소켓 I/O라 코루틴 취소를 듣지 않으므로, 취소되면 이 세션을 닫아(소켓을 닫아) 바로 끝낸다.
     * @param endpoint 접속할 서버 정보
     * @return 연 결과
     */
    suspend fun open(endpoint: FtpEndpoint): SessionOpening {
        check(openRequested.compareAndSet(false, true)) { "FtpSession can be opened only once" }
        return coroutineScope {
            val opening = async(Dispatchers.IO) { withClient { client -> openOn(client, endpoint) } }
            try {
                opening.await()
            } catch (e: CancellationException) {
                withContext(NonCancellable) { close() }
                throw e
            }
        }
    }

    /**
     * [client]로 [open]의 절차를 실행한다. ioMutex를 쥔 상태에서 부른다.
     * @param client 이 세션의 FTP 클라이언트
     * @param endpoint 접속할 서버 정보
     * @return 연 결과
     */
    private suspend fun openOn(client: SessionFtpClient, endpoint: FtpEndpoint): SessionOpening {
        val result = try {
            client.connectTimeout = CONNECT_TIMEOUT_MS
            client.setControlEncoding(endpoint.encoding)
            // 응답 읽기의 상한은 세션이 정해 둔 값([ioTimeoutMillis])으로 접속할 때 걸린다
            client.connect(ftpServerOf(endpoint.address), endpoint.port)
            val checked = loginAndCheck(client, endpoint)
            if (checked != SessionOpening.Opened) checked
            else if (markLoggedIn(client.printWorkingDirectory())) SessionOpening.Opened
            else SessionOpening.Closed
        } catch (e: IOException) {
            failedOpening(client, e)
        }
        if (result != SessionOpening.Opened) closeQuietly(client)
        return result
    }

    /**
     * 접속한 [client]로 로그인하고 전송 방식을 정한 뒤, 이 서버의 목록을 해석할 수 있는지 확인한다.
     * @param client 서버에 접속한 FTPClient
     * @param endpoint 로그인 정보를 담은 서버 정보
     * @return 쓸 수 있으면 [SessionOpening.Opened]. 로그인이 거부됐으면 그 응답을 담은 거부, 목록을 해석할 수 없으면 [SessionOpening.ListingFormatUnknown]
     * @throws IOException 응답을 읽지 못한 경우
     */
    private fun loginAndCheck(client: SessionFtpClient, endpoint: FtpEndpoint): SessionOpening {
        if (!client.login(endpoint.userId, endpoint.password)) return SessionOpening.Refused(client.replyString.trim())
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        if (!FtpListingFormat.isReadable(client)) return SessionOpening.ListingFormatUnknown
        return SessionOpening.Opened
    }

    /**
     * 여는 도중 난 IOException을 결과로 바꾼다. 종료 요청으로 소켓이 닫힌 실패는 닫힘으로, 접속 인사·응답의 421(commons-net이
     * [FTPConnectionClosedException]으로 던진다)은 서버의 거부로 본다.
     * @param client 이 세션의 FTP 클라이언트
     * @param cause 여는 도중 난 예외
     * @return 연 결과
     */
    private suspend fun failedOpening(client: SessionFtpClient, cause: IOException): SessionOpening = when {
        stateMutex.withLock { isClosed } -> SessionOpening.Closed
        cause is FTPConnectionClosedException && client.replyCode == FTPReply.SERVICE_NOT_AVAILABLE -> SessionOpening.Refused(client.replyString.trim())
        else -> SessionOpening.Unreachable(cause)
    }

    /**
     * 쓰지 않을 연결을 로그아웃 후 닫는다. 이미 닫혔거나 응답이 없어도 실패를 알리지 않는다 — 연결을 남기지 않는 것이 목적이다.
     * @param client 닫을 FTPClient
     */
    private fun closeQuietly(client: SessionFtpClient) {
        if (client.isConnected) runCatching { client.logout() }
        runCatching { client.disconnect() }
    }

    /**
     * 로그인 성공 상태로 전환한다. 새 연결의 현재 디렉터리는 로그인 직후의 홈이다.
     * 종료가 요청된 세션은 전환하지 않는다 — 종료 요청과 같은 잠금 안에서 판단하므로, 여는 작업과 겹친 종료 요청이 로그인 상태로 뒤집히지 않는다.
     * @param home 로그인 직후의 현재 디렉터리(FTP 경로). 서버가 알려주지 않았으면 null
     * @return 로그인 상태로 전환했으면 true, 종료가 요청돼 전환하지 않았으면 false
     */
    suspend fun markLoggedIn(home: String?): Boolean = stateMutex.withLock {
        if (isClosed) return@withLock false
        client.recordLogin(home)
        isLoggedIn = true
        true
    }

    /**
     * 서버 쪽에서 끊긴 세션의 로그인 상태를 해제한다. 진행 중인 작업은 다음 명령에서 [withConnectedClient]가 끊김으로 알린다.
     * 로그인된 세션이 끊긴 경우에만 [onLost]를 부른다 — 앱이 먼저 닫은 세션([close])의 진행 중 명령이 소켓이 닫혀 실패한 것은 끊김이 아니다.
     */
    private suspend fun markConnectionDropped() {
        val wasLoggedIn = stateMutex.withLock { isLoggedIn.also { isLoggedIn = false } }
        if (wasLoggedIn) onLost()
    }

    /**
     * 진행 중인 FTP 명령을 기다리지 않고 세션을 닫는다. 닫은 세션은 다시 열리지 않는다. 여러 번 불러도 된다.
     * 로그인 상태를 먼저 해제하므로 [withConnectedClient]는 이를 연결 끊김으로 알리며, 끊김([onLost])으로 보지 않는다.
     * 진행 중인 명령이 없으면 ioMutex를 얻어 로그아웃한 뒤 연결을 닫는다.
     * 진행 중이면 그 명령이 쓰는 소켓(제어·데이터)만 중단으로 닫는다(업로드가 서버에 파일 끝으로 읽히지 않도록) — FTPClient는 스레드에 안전하지 않으므로 잠금 밖에서 명령을 보내거나 내부 상태를 바꾸지 않는다.
     * 그 명령은 IOException을 받아 자기 잠금 안에서 세션을 정리하고, 이 함수는 ioMutex를 얻은 뒤 남은 연결을 닫는다.
     * 여는 중인 세션([open])도 닫을 수 있다 — 닫을 소켓이 아직 없어도 종료 요청이 남아 열린 것으로 끝나지 않고, 그 뒤에 만드는 소켓은 바로 닫힌다.
     * 로그아웃은 네트워크 I/O이므로 부르는 쪽의 스레드(화면의 Main 등)와 무관하게 IO 스레드에서 한다.
     */
    suspend fun close(): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            isClosed = true
            isLoggedIn = false
        }
        if (ioMutex.tryLock()) {
            try {
                closeQuietly(client)
                // 아직 잠금을 얻지 못한 여는 작업이 뒤이어 소켓을 만들어도 바로 닫히게 한다
                sockets.closeAll()
            } finally {
                ioMutex.unlock()
            }
        } else {
            sockets.closeAll()
            ioMutex.withLock { runCatching { client.disconnect() } }
        }
    }

    companion object {
        /** 소켓 읽기·쓰기 한 번을 기다리는 기본 상한(ms). FileZilla의 기본 무응답 제한(20초)과 비슷한 수준이다. */
        const val IO_TIMEOUT_MILLIS = 15_000
        /** 서버에 접속(TCP 연결)하는 데 기다리는 상한(ms). */
        private const val CONNECT_TIMEOUT_MS = 10_000
    }
}

/**
 * 세션을 쓸 수 없게 한 IOException(서버가 닫음·응답을 읽지 못함·접속하지 못함)을 원인으로 바꾼다.
 * @param cause 세션을 쓸 수 없게 한 예외
 * @return 연결이 끊긴 원인
 */
internal fun connectionFailureReason(cause: IOException): FailureReason =
    if (cause is FTPConnectionClosedException) FailureReason.ConnectionClosed
    else FailureReason.NoResponse(cause.message ?: cause.javaClass.simpleName)
