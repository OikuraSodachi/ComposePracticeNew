package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTPClient 인스턴스를 단일 @Singleton으로 소유하며 연결 상태를 StateFlow로 노출한다.
 * FtpCommandExecutor, FtpTransferrer, FtpKeepAlive가 공유하는 공통 상태 허브.
 */
@Singleton
class FtpConnectionState @Inject constructor() {

    internal val client = FTPClient()
    /** isLoggedIn, connectedServer, _isConnected, _isConnecting 갱신을 직렬화한다. */
    internal val stateMutex = Mutex()
    /** connect/listFiles FTP 명령을 직렬화한다. disconnect는 이 lock을 우회해 진행 중 I/O를 즉시 중단한다. */
    internal val ioMutex = Mutex()
    /** 진행 중인 모든 FTP IO 작업의 루트 Job — 연결 끊김 시 취소해 자식 코루틴을 일괄 중단한다. */
    @Volatile internal var ftpIoJob: CompletableJob = SupervisorJob()

    internal var connectedServer: String? = null
    internal var isLoggedIn = false

    private val _isConnected = MutableStateFlow(false)
    /** FTP 서버와 연결 및 로그인이 모두 완료된 경우 true를 방출한다. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    /** connect() 또는 disconnect() 호출이 진행 중인 경우 true를 방출한다. */
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val keepAlive = FtpKeepAlive(client, ioMutex, isConnected, ::markConnectionDropped)

    /**
     * FTP 서버에 TCP 연결 후 로그인한다. 이미 연결된 경우 먼저 해제하고 재연결한다.
     * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
     * @param port FTP 포트 (기본 21)
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @param encoding 컨트롤 채널 문자 인코딩 (예: UTF-8, EUC-KR)
     * @return 연결 및 로그인 성공 시 true
     */
    suspend fun connect(address: String, port: Int, userId: String, password: String, encoding: String): Boolean {
        stateMutex.withLock { _isConnecting.value = true }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val server = address.removePrefix("ftp://")
                    if (client.isConnected) {
                        runCatching { client.logout() }
                        runCatching { client.disconnect() }
                        stateMutex.withLock {
                            isLoggedIn = false
                            _isConnected.value = false
                        }
                    }
                    client.connectTimeout = CONNECT_TIMEOUT_MS
                    client.setControlEncoding(encoding)
                    client.connect(server, port)
                    client.soTimeout = SO_TIMEOUT_MS
                    val loggedIn = client.login(userId, password)
                    if (loggedIn) {
                        client.enterLocalPassiveMode()
                        client.setFileType(FTP.BINARY_FILE_TYPE)
                        stateMutex.withLock {
                            connectedServer = server
                            isLoggedIn = true
                            _isConnected.value = true
                        }
                        keepAlive.start()
                    } else {
                        runCatching { client.disconnect() }
                    }
                    loggedIn
                } catch (e: UnknownHostException) {
                    false
                } catch (e: Exception) {
                    false
                } finally {
                    stateMutex.withLock { _isConnecting.value = false }
                }
            }
        }
    }

    /**
     * 현재 연결을 로그아웃 후 해제한다. isConnected를 false로 갱신한다.
     * ioMutex를 우회해 소켓을 즉시 닫으므로 진행 중인 listFiles가 IOException으로 중단된다.
     * 이미 미연결 상태이면 아무 동작도 하지 않는다.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (!client.isConnected) return@withContext
        keepAlive.stop()
        ftpIoJob.cancel()
        stateMutex.withLock {
            isLoggedIn = false
            connectedServer = null
            _isConnected.value = false
            _isConnecting.value = true
        }
        runCatching { client.logout() }
        runCatching { client.disconnect() }
        stateMutex.withLock { _isConnecting.value = false }
        ftpIoJob = SupervisorJob()
    }

    /** FTP 연결이 서버 측에서 끊긴 경우 isLoggedIn과 _isConnected를 false로 초기화하고 ftpIoJob을 취소한다. */
    internal suspend fun markConnectionDropped() {
        stateMutex.withLock {
            isLoggedIn = false
            _isConnected.value = false
        }
        ftpIoJob.cancel()
        ftpIoJob = SupervisorJob()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS = 15_000
    }
}
