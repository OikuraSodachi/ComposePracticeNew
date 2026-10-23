package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 단일 FTPClient와 그 잠금·로그인 상태를 캡슐화하는 FTP 세션.
 * 클라이언트와 Mutex는 외부로 노출하지 않으며, 명령 실행은 [withClient] 또는 [withConnectedClient]를 통해서만 가능하다.
 * 잠금 규칙: 로그인 상태 플래그는 stateMutex, FTP 명령 I/O는 ioMutex로 직렬화한다.
 * @param client 세션이 단독 소유하는 FTP 클라이언트. 외부에는 [withClient]로만 노출한다. 앱에서는 새 FTPClient를, 테스트에서는 가짜 클라이언트를 넘긴다
 */
@Singleton
class FtpSession internal constructor(private val client: FTPClient) {

    /** 새 FTPClient를 소유하는 세션을 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor() : this(FTPClient())

    /** isLoggedIn, _isConnected, _isConnecting 갱신을 직렬화한다. */
    private val stateMutex = Mutex()
    /** FTP 명령 I/O를 직렬화한다. [closeBypassingIoLock]은 이 lock을 기다리지 않고 진행 중 I/O의 소켓을 닫아 즉시 중단한다. */
    private val ioMutex = Mutex()
    /** 로그인까지 완료된 상태인지 여부. stateMutex로 보호한다. */
    private var isLoggedIn = false
    /** [closeBypassingIoLock]이 요청된 횟수. stateMutex 안에서 늘리고, ioMutex를 쥔 연결 작업이 잠금 없이 읽으므로 원자 값으로 둔다. */
    private val bypassCloseRequests = AtomicInteger()
    /** [client]가 연 제어·데이터 소켓. [closeBypassingIoLock]이 잠금 밖에서 FTPClient를 건드리지 않고 진행 중 I/O를 깨우려고 닫는다. */
    private val sockets = AbortableSocketFactory().also { client.setSocketFactory(it) }

    /**
     * 세션의 현재 디렉터리(FTP 경로). 로그인 직후에는 [homeDirectory]이고, 이 앱이 CWD로 옮길 때마다 갱신한다. 알 수 없으면 null이다.
     * 현재 디렉터리를 지우거나 옮기는 명령을 그 밖에서 보내려고 기록한다([FtpCommandExecutor]). ioMutex를 쥔 블록 안에서만 읽고 쓴다.
     */
    internal var workingDirectory: String? = null

    /**
     * 로그인 직후의 현재 디렉터리(홈, FTP 경로). 서버가 알려주지 않았으면 null이다.
     * 로그인한 사용자가 들어갈 수 있는 디렉터리이므로, 대상 밖으로 나가는 이동이 거부될 때 다시 시도할 곳으로 쓴다([FtpCommandExecutor]).
     * 연결할 때 ioMutex를 쥔 채 정하고, ioMutex를 쥔 블록 안에서만 읽는다.
     */
    internal var homeDirectory: String? = null
        private set

    private val _isConnected = MutableStateFlow(false)
    /** FTP 서버와 연결 및 로그인이 모두 완료된 경우 true를 방출한다. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    /** connect() 또는 disconnect() 호출이 진행 중인 경우 true를 방출한다. */
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /** 소켓 연결과 로그인이 모두 유효하면 true를 반환한다. */
    suspend fun isReady(): Boolean = stateMutex.withLock { client.isConnected && isLoggedIn }

    /** 소켓이 열려 있으면 true를 반환한다. 잠금 없이 조회한다. */
    fun isSocketOpen(): Boolean = client.isConnected

    /**
     * ioMutex를 확보한 상태로 [block]에 FTPClient를 넘겨 실행한다. 스레드 전환은 호출부가 결정한다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 반환값
     */
    suspend fun <T> withClient(block: suspend (FTPClient) -> T): T = ioMutex.withLock { block(client) }

    /**
     * 연결된 상태에서만 [withClient]로 [block]을 실행한다. FTP 명령 실패의 의미를 정하는 경계이며, 규칙은 다음 하나뿐이다.
     * - [block]이 돌려준 결과: 서버 거부 응답·데이터 스트림·로컬 파일 실패처럼 제어 채널의 응답 순서가 유지된 결과다. 그대로 돌려준다
     * - [block]이 던진 IOException: 서버가 연결을 닫았거나([FTPConnectionClosedException]) 응답을 읽다 실패(타임아웃 등)한 것이다.
     *   늦게 온 응답이 다음 명령의 응답으로 읽혀 순서가 어긋날 수 있고 commons-net에는 이를 다시 맞출 방법이 없으므로, 세션을 종료하고 [IoResult.ConnectionLost]를 돌려준다
     * - 그 밖의 예외(구현 오류): 명령이 어디까지 진행됐는지 알 수 없어 응답 순서를 보장할 수 없으므로, 세션을 종료하고 그대로 전파한다
     * 그래서 [block] 안에서 세션을 유지할 실패는 예외로 던지지 말고 결과로 돌려줘야 한다.
     * 잠금을 얻은 시점에 미연결이면 실행하지 않고 [IoResult.ConnectionLost]를 돌려준다. 취소는 그대로 전파한다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 결과, 또는 연결 끊김
     */
    suspend fun <T> withConnectedClient(block: suspend (FTPClient) -> IoResult<T>): IoResult<T> = withClient { client ->
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
     * 응답 순서를 알 수 없게 된 세션을 끊김 상태로 바꾸고, 그 제어 채널을 다시 쓰지 않도록 소켓을 닫는다.
     * 예외로 전파할 수 없는 경로(취소 정리 중 응답을 읽지 못한 경우 등)에서도 호출한다. ioMutex를 쥔 상태에서 호출한다.
     * 취소된 코루틴에서도 끝까지 실행한다 — 상태 잠금을 기다리다 취소로 멈추면 쓸 수 없는 세션이 로그인 상태로 남는다.
     */
    internal suspend fun endUnusableSession() {
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

    /** FTP 연결이 서버 측에서 끊긴 경우 로그인 상태를 초기화한다. 진행 중인 작업은 다음 명령에서 [withConnectedClient]가 끊김으로 알린다. */
    suspend fun markConnectionDropped() {
        stateMutex.withLock {
            isLoggedIn = false
            _isConnected.value = false
        }
    }

    /** 연결 진행 중 여부를 갱신한다. */
    internal suspend fun setConnecting(value: Boolean) = stateMutex.withLock { _isConnecting.value = value }

    /**
     * 로그인 성공 상태로 전환한다. 새 연결의 현재 디렉터리는 로그인 직후의 홈이다.
     * [closeMark]를 읽은 뒤 종료가 요청됐으면 전환하지 않는다 — 종료 요청과 같은 잠금 안에서 판단하므로, 연결 작업과 겹친 종료 요청이 연결 상태로 뒤집히지 않는다.
     * @param closeMark 연결 작업을 시작할 때 읽은 종료 요청 표지([closeRequestMark])
     * @param home 로그인 직후의 현재 디렉터리(FTP 경로). 서버가 알려주지 않았으면 null
     * @return 로그인 상태로 전환했으면 true, 그 사이 종료가 요청돼 전환하지 않았으면 false
     */
    internal suspend fun markLoggedIn(closeMark: Int, home: String?): Boolean = stateMutex.withLock {
        if (isCloseRequestedSince(closeMark)) return@withLock false
        homeDirectory = home
        workingDirectory = home
        isLoggedIn = true
        _isConnected.value = true
        true
    }

    /**
     * 지금까지 [closeBypassingIoLock]이 요청된 횟수를 돌려준다. 연결 작업이 시작할 때 읽어 두었다가 [isCloseRequestedSince]·[markLoggedIn]으로 그 사이의 종료 요청을 확인한다.
     * @return 종료 요청 표지
     */
    internal fun closeRequestMark(): Int = bypassCloseRequests.get()

    /**
     * [mark]를 읽은 뒤 [closeBypassingIoLock]이 요청됐는지 판별한다. 연결 작업이 끊긴 연결을 스스로 다시 열기 전에, 그 끊김이 종료 요청 때문인지 확인하는 데 쓴다.
     * @param mark [closeRequestMark]로 읽어 둔 표지
     * @return 그 뒤 종료가 요청됐으면 true
     */
    internal fun isCloseRequestedSince(mark: Int): Boolean = bypassCloseRequests.get() != mark

    /** 로그인 해제 상태로 전환한다. */
    internal suspend fun markLoggedOut() = stateMutex.withLock {
        isLoggedIn = false
        _isConnected.value = false
    }

    /**
     * 진행 중인 FTP 명령을 기다리지 않고 연결을 끊는다. 로그인 상태를 먼저 해제하므로 [withConnectedClient]는 이를 연결 끊김으로 알린다.
     * 진행 중인 명령이 없으면 ioMutex를 얻어 로그아웃한 뒤 연결을 닫는다.
     * 진행 중이면 그 명령이 쓰는 소켓(제어·데이터)만 닫는다 — FTPClient는 스레드에 안전하지 않으므로 잠금 밖에서 명령을 보내거나 내부 상태를 바꾸지 않는다.
     * 그 명령은 IOException을 받아 자기 잠금 안에서 세션을 정리하고, 이 함수는 ioMutex를 얻은 뒤 남은 연결을 닫는다.
     * 진행 중인 연결 작업이 이 종료를 연결 결과에 반영하도록([markLoggedIn], [isCloseRequestedSince]), 소켓을 닫기 전에 요청을 기록한다.
     * 호출 전 소켓이 열려 있는지 [isSocketOpen]으로 확인해야 한다.
     */
    internal suspend fun closeBypassingIoLock() {
        stateMutex.withLock {
            bypassCloseRequests.incrementAndGet()
            isLoggedIn = false
            _isConnected.value = false
            _isConnecting.value = true
        }
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
        /** 연결되지 않은 상태에서 명령을 실행하려 할 때 표시할 메시지. */
        private const val NOT_CONNECTED_MESSAGE = "FTP 서버에 연결되어 있지 않습니다"
        /** 서버가 연결을 닫았을 때 표시할 메시지. */
        private const val CONNECTION_LOST_MESSAGE = "FTP 서버와의 연결이 끊겼습니다"
        /** 서버 응답을 받지 못해 세션을 종료했을 때 표시할 메시지. */
        private const val NO_RESPONSE_MESSAGE = "FTP 서버 응답을 받지 못해 연결을 종료했습니다"
    }
}
