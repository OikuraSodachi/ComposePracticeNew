package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.IOException
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
    /** FTP 명령 I/O를 직렬화한다. [closeBypassingIoLock]은 이 lock을 우회해 진행 중 I/O를 즉시 중단한다. */
    private val ioMutex = Mutex()
    /** 로그인까지 완료된 상태인지 여부. stateMutex로 보호한다. */
    private var isLoggedIn = false

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
     * 연결된 상태에서만 [withClient]로 [block]을 실행하고, 세션을 더 쓸 수 없게 된 실패를 [RemoteConnectionLostException]으로 바꿔 던진다.
     * 잠금을 얻은 시점에 미연결이면 실행하지 않는다. [FtpItemFailure]는 제어 채널의 응답 순서가 유지된 실패이므로 그대로 전파한다.
     * 그 밖의 IOException은 서버가 연결을 닫았거나([FTPConnectionClosedException]) 응답을 읽다 실패(타임아웃 등)한 것이다.
     * 늦게 온 응답이 다음 명령의 응답으로 읽혀 순서가 어긋날 수 있고 commons-net에는 이를 다시 맞출 방법이 없으므로, 세션을 종료한다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 반환값
     */
    suspend fun <T> withConnectedClient(block: suspend (FTPClient) -> T): T = withClient { client ->
        if (!isReady()) throw RemoteConnectionLostException(NOT_CONNECTED_MESSAGE)
        try {
            block(client)
        } catch (e: RemoteConnectionLostException) {
            throw e
        } catch (e: FtpItemFailure) {
            throw e
        } catch (e: IOException) {
            endUnusableSession()
            throw RemoteConnectionLostException(unusableSessionMessage(e), e)
        }
    }

    /**
     * 응답 순서를 알 수 없게 된 세션을 끊김 상태로 바꾸고, 그 제어 채널을 다시 쓰지 않도록 소켓을 닫는다.
     * 예외로 전파할 수 없는 경로(취소 정리 중 응답을 읽지 못한 경우 등)에서도 호출한다. ioMutex를 쥔 상태에서 호출한다.
     */
    internal suspend fun endUnusableSession() {
        markConnectionDropped()
        runCatching { client.disconnect() }
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

    /** 로그인 성공 상태로 전환한다. */
    internal suspend fun markLoggedIn() = stateMutex.withLock {
        isLoggedIn = true
        _isConnected.value = true
    }

    /** 로그인 해제 상태로 전환한다. */
    internal suspend fun markLoggedOut() = stateMutex.withLock {
        isLoggedIn = false
        _isConnected.value = false
    }

    /**
     * ioMutex를 우회해 로그아웃·소켓 종료를 수행한다. 진행 중인 FTP 명령은 IOException으로 중단되며,
     * 로그인 상태를 먼저 해제하므로 [withConnectedClient]는 이를 연결 끊김으로 알린다.
     * 호출 전 소켓이 열려 있는지 [isSocketOpen]으로 확인해야 한다.
     */
    internal suspend fun closeBypassingIoLock() {
        stateMutex.withLock {
            isLoggedIn = false
            _isConnected.value = false
            _isConnecting.value = true
        }
        runCatching { client.logout() }
        runCatching { client.disconnect() }
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
