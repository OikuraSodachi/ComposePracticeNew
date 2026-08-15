package com.todokanai.composepracticenew.data.ftp

import android.util.Log
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTPClient 인스턴스를 단일 @Singleton으로 소유하며 연결 상태를 StateFlow로 노출한다.
 * FtpFileSystem과 FtpForegroundService가 공유하는 공통 상태 허브.
 */
@Singleton
class FtpConnectionState @Inject constructor() {

    private val client = FTPClient()
    /** isLoggedIn, connectedServer, _isConnected, _isConnecting 갱신을 직렬화한다. */
    private val stateMutex = Mutex()
    /** connect/listFiles FTP 명령을 직렬화한다. disconnect는 이 lock을 우회해 진행 중 I/O를 즉시 중단한다. */
    private val ioMutex = Mutex()

    private var connectedServer: String? = null
    private var isLoggedIn = false

    private val _isConnected = MutableStateFlow(false)
    /** FTP 서버와 연결 및 로그인이 모두 완료된 경우 true를 방출한다. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    /** connect() 또는 disconnect() 호출이 진행 중인 경우 true를 방출한다. */
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /**
     * FTP 서버에 TCP 연결 후 로그인한다. 이미 연결된 경우 먼저 해제하고 재연결한다.
     * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
     * @param port FTP 포트 (기본 21)
     * @param userId FTP 로그인 아이디
     * @param password FTP 로그인 비밀번호
     * @return 연결 및 로그인 성공 시 true
     */
    suspend fun connect(address: String, port: Int, userId: String, password: String): Boolean {
        stateMutex.withLock { _isConnecting.value = true }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val server = address.removePrefix("ftp://")
                    Log.d(TAG, "connect: server=$server userId=$userId")
                    if (client.isConnected) {
                        runCatching { client.logout() }
                        runCatching { client.disconnect() }
                        stateMutex.withLock {
                            isLoggedIn = false
                            _isConnected.value = false
                        }
                    }
                    client.connectTimeout = CONNECT_TIMEOUT_MS
                    client.connect(server, port)
                    client.soTimeout = SO_TIMEOUT_MS
                    val loggedIn = client.login(userId, password)
                    if (loggedIn) {
                        client.enterLocalPassiveMode()
                        stateMutex.withLock {
                            connectedServer = server
                            isLoggedIn = true
                            _isConnected.value = true
                        }
                        Log.d(TAG, "connect: success server=$server")
                    } else {
                        runCatching { client.disconnect() }
                        Log.e(TAG, "connect: 로그인 실패 server=$server")
                    }
                    loggedIn
                } catch (e: UnknownHostException) {
                    Log.e(TAG, "connect: 호스트 해석 실패 — 주소를 확인하세요")
                    false
                } catch (e: Exception) {
                    Log.e(TAG, "connect: ${e::class.simpleName} msg=${e.message}")
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
        stateMutex.withLock { _isConnecting.value = true }
        runCatching { client.logout() }
        runCatching { client.disconnect() }
        stateMutex.withLock {
            isLoggedIn = false
            connectedServer = null
            _isConnected.value = false
            _isConnecting.value = false
            Log.d(TAG, "disconnect: 완료")
        }
    }

    /**
     * 주어진 원격 경로의 파일 목록을 반환한다. 미연결 시 빈 목록을 반환한다.
     * @param path ftp://server/path 형식의 절대 경로
     */
    suspend fun listFiles(path: String): List<FileEntry> {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "listFiles: 미연결 또는 미인증 path=$path")
                return emptyList()
            }
            extractFtpPath(path).also {
                Log.d(TAG, "listFiles: server=$connectedServer ftpPath=$it")
            }
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.listFiles(ftpPath)
                        ?.filter { it.name != "." && it.name != ".." }
                        ?.map { file ->
                            FileEntry(
                                name = file.name,
                                path = path.trimEnd('/') + "/" + file.name,
                                isDirectory = file.isDirectory,
                                size = file.size,
                                lastModified = file.timestamp?.timeInMillis ?: 0L
                            )
                        } ?: emptyList()
                }.onFailure { e ->
                    Log.e(TAG, "listFiles: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun extractFtpPath(path: String): String {
        val withoutScheme = path.removePrefix("ftp://")
        val slashIndex = withoutScheme.indexOf('/')
        return if (slashIndex == -1) "/" else withoutScheme.substring(slashIndex)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS = 15_000
        private const val TAG = "FtpConnectionState"
    }
}
