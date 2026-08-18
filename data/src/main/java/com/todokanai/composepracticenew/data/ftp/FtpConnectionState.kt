package com.todokanai.composepracticenew.data.ftp

import android.util.Log
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTPClient 인스턴스를 단일 @Singleton으로 소유하며 연결 상태를 StateFlow로 노출한다.
 * FtpRepositoryImpl과 FtpForegroundService가 공유하는 공통 상태 허브.
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
                        client.setFileType(FTP.BINARY_FILE_TYPE)
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

    /**
     * remotePath의 파일을 localPath에 스트리밍 다운로드한다.
     * FTPClient.retrieveFileStream()으로 원격 파일을 읽어 localPath에 쓰며 진행률을 방출한다.
     * 미연결 시 error ProgressState를 방출하고 종료한다.
     * @param remotePath ftp://server/path 형식의 원격 파일 절대 경로
     * @param localPath 저장할 로컬 파일의 절대 경로
     */
    fun download(remotePath: String, localPath: String): Flow<ProgressState> = flow<ProgressState> {
        val (connected, ftpPath) = stateMutex.withLock {
            (client.isConnected && isLoggedIn) to extractFtpPath(remotePath)
        }
        if (!connected) {
            emit(ProgressState(error = "Not connected"))
            return@flow
        }
        ioMutex.withLock {
            val localFile = File(localPath)
            localFile.parentFile?.mkdirs()
            val totalBytes = runCatching { client.mlistFile(ftpPath)?.size ?: -1L }.getOrDefault(-1L)
            val inputStream = client.retrieveFileStream(ftpPath)
            if (inputStream == null) {
                emit(ProgressState(error = "retrieveFileStream 실패: ${client.replyString.trim()}"))
                return@withLock
            }
            var transferFailed = false
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var writtenBytes = 0L
                localFile.outputStream().use { output ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        writtenBytes += bytesRead
                        emit(buildProgressState(totalBytes, writtenBytes, localFile.name))
                    }
                }
            } catch (e: CancellationException) {
                runCatching { localFile.delete() }
                throw e
            } catch (e: Exception) {
                transferFailed = true
                Log.e(TAG, "download: ${e::class.simpleName} path=$remotePath msg=${e.message}")
                emit(ProgressState(error = e.message ?: "download 실패"))
            } finally {
                runCatching { inputStream.close() }
                val ok = runCatching { client.completePendingCommand() }.getOrDefault(false)
                if (!transferFailed && !ok) emit(ProgressState(error = "전송 미완료: ${client.replyString.trim()}"))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * localPath의 파일을 remotePath에 스트리밍 업로드한다.
     * FTPClient.storeFileStream()에 로컬 파일 스트림을 펌핑하며 진행률을 방출한다.
     * 미연결 시 error ProgressState를 방출하고 종료한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remotePath 저장될 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    fun upload(localPath: String, remotePath: String): Flow<ProgressState> = flow<ProgressState> {
        val (connected, ftpPath) = stateMutex.withLock {
            (client.isConnected && isLoggedIn) to extractFtpPath(remotePath)
        }
        if (!connected) {
            emit(ProgressState(error = "Not connected"))
            return@flow
        }
        ioMutex.withLock {
            val localFile = File(localPath)
            val totalBytes = localFile.length()
            val outputStream = client.storeFileStream(ftpPath)
            if (outputStream == null) {
                emit(ProgressState(error = "storeFileStream 실패: ${client.replyString.trim()}"))
                return@withLock
            }
            var transferFailed = false
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var writtenBytes = 0L
                localFile.inputStream().use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        writtenBytes += bytesRead
                        emit(buildProgressState(totalBytes, writtenBytes, localFile.name))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transferFailed = true
                Log.e(TAG, "upload: ${e::class.simpleName} path=$remotePath msg=${e.message}")
                emit(ProgressState(error = e.message ?: "upload 실패"))
            } finally {
                runCatching { outputStream.close() }
                val ok = runCatching { client.completePendingCommand() }.getOrDefault(false)
                if (!transferFailed && !ok) emit(ProgressState(error = "전송 미완료: ${client.replyString.trim()}"))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 현재 FTP 작업 디렉터리의 절대 경로를 반환한다. 미연결 시 빈 문자열을 반환한다. */
    suspend fun getWorkingDirectory(): String {
        stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "getWorkingDirectory: 미연결 또는 미인증")
                return ""
            }
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.printWorkingDirectory() ?: ""
                }.onFailure { e ->
                    Log.e(TAG, "getWorkingDirectory: ${e::class.simpleName} msg=${e.message}")
                }.getOrDefault("")
            }
        }
    }

    /**
     * path로 FTP 작업 디렉터리를 변경한다. 미연결 시 false를 반환한다.
     * @param path 이동할 원격 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun changeDirectory(path: String): Boolean {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "changeDirectory: 미연결 또는 미인증 path=$path")
                return false
            }
            extractFtpPath(path)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.changeWorkingDirectory(ftpPath)
                }.onFailure { e ->
                    Log.e(TAG, "changeDirectory: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault(false)
            }
        }
    }

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다. 미연결 시 false를 반환한다.
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     */
    suspend fun rename(fromPath: String, toPath: String): Boolean {
        var fromFtpPath = ""
        var toFtpPath = ""
        stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "rename: 미연결 또는 미인증 from=$fromPath")
                return false
            }
            fromFtpPath = extractFtpPath(fromPath)
            toFtpPath = extractFtpPath(toPath)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.rename(fromFtpPath, toFtpPath)
                }.onFailure { e ->
                    Log.e(TAG, "rename: ${e::class.simpleName} from=$fromPath msg=${e.message}")
                }.getOrDefault(false)
            }
        }
    }

    /**
     * path의 파일을 원격 서버에서 삭제한다. 미연결 시 false를 반환한다.
     * @param path 삭제할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun deleteFile(path: String): Boolean {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "deleteFile: 미연결 또는 미인증 path=$path")
                return false
            }
            extractFtpPath(path)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.deleteFile(ftpPath)
                }.onFailure { e ->
                    Log.e(TAG, "deleteFile: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault(false)
            }
        }
    }

    /**
     * path에 새 원격 디렉터리를 생성한다. 미연결 시 false를 반환한다.
     * @param path 생성할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun makeDirectory(path: String): Boolean {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "makeDirectory: 미연결 또는 미인증 path=$path")
                return false
            }
            extractFtpPath(path)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.makeDirectory(ftpPath)
                }.onFailure { e ->
                    Log.e(TAG, "makeDirectory: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault(false)
            }
        }
    }

    /**
     * path의 빈 디렉터리를 원격 서버에서 삭제한다. 미연결 시 false를 반환한다.
     * @param path 삭제할 빈 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun removeDirectory(path: String): Boolean {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "removeDirectory: 미연결 또는 미인증 path=$path")
                return false
            }
            extractFtpPath(path)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.removeDirectory(ftpPath)
                }.onFailure { e ->
                    Log.e(TAG, "removeDirectory: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault(false)
            }
        }
    }

    /**
     * path 파일의 크기를 바이트 단위로 반환한다. 조회 실패 또는 미연결 시 -1을 반환한다.
     * @param path 크기를 조회할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun getFileSize(path: String): Long {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "getFileSize: 미연결 또는 미인증 path=$path")
                return -1L
            }
            extractFtpPath(path)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.mlistFile(ftpPath)?.size ?: -1L
                }.onFailure { e ->
                    Log.e(TAG, "getFileSize: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault(-1L)
            }
        }
    }

    /**
     * path 파일의 최종 수정 시각을 FTP MDTM 형식 문자열로 반환한다. 조회 실패 또는 미연결 시 빈 문자열을 반환한다.
     * @param path 수정 시각을 조회할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun getModificationTime(path: String): String {
        val ftpPath = stateMutex.withLock {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "getModificationTime: 미연결 또는 미인증 path=$path")
                return ""
            }
            extractFtpPath(path)
        }
        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.getModificationTime(ftpPath) ?: ""
                }.onFailure { e ->
                    Log.e(TAG, "getModificationTime: ${e::class.simpleName} path=$path msg=${e.message}")
                }.getOrDefault("")
            }
        }
    }

    private fun buildProgressState(totalBytes: Long, writtenBytes: Long, fileName: String) = ProgressState(
        totalBytes = totalBytes,
        writtenBytes = writtenBytes,
        progress = if (totalBytes > 0) (writtenBytes * 100 / totalBytes).toInt() else null,
        progressFloat = if (totalBytes > 0) writtenBytes.toFloat() / totalBytes else null,
        currentFileName = fileName
    )

    private fun extractFtpPath(path: String): String {
        val withoutScheme = path.removePrefix("ftp://")
        val slashIndex = withoutScheme.indexOf('/')
        return if (slashIndex == -1) "/" else withoutScheme.substring(slashIndex)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS = 15_000
        private const val BUFFER_SIZE = 8 * 1024
        private const val TAG = "FtpConnectionState"
    }
}
