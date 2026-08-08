package com.todokanai.composepracticenew.data.ftp

import android.util.Log
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.todokanai.composepracticenew.repository.FtpClientRepository
import org.apache.commons.net.ftp.FTPClient
import java.net.UnknownHostException

/** FTPClient를 유지하며 FTP 원격 파일 시스템에 접근한다. 사용 전 connect()를 호출해야 한다. */
class FtpFileSystem(
    private val client: FTPClient
) : FtpClientRepository {

    private val mutex = Mutex()
    private var connectedServer: String? = null
    private var isLoggedIn = false

    /** FTP 서버에 연결하고 로그인한다. RemoteStorageItem 클릭 시 호출된다. */
    override suspend fun connect(address: String, port: Int, userId: String, password: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val server = address.removePrefix("ftp://")
            Log.d(TAG, "connect: server=$server userId=$userId")
            runCatching {
                if (client.isConnected) {
                    runCatching { client.logout() }
                    runCatching { client.disconnect() }
                    isLoggedIn = false
                }
                client.connectTimeout = 10_000
                client.connect(server, port)
                client.soTimeout = 15_000
                val loggedIn = client.login(userId, password)
                isLoggedIn = loggedIn
                if (loggedIn) {
                    client.enterLocalPassiveMode()
                    connectedServer = server
                    Log.d(TAG, "connect: success server=$server")
                } else {
                    Log.e(TAG, "connect: 로그인 실패 server=$server")
                }
                loggedIn
            }.onFailure { e ->
                when (e) {
                    is UnknownHostException -> Log.e(TAG, "connect: 호스트 해석 실패 server=$server — 주소를 확인하세요")
                    else -> Log.e(TAG, "connect: ${e::class.simpleName} server=$server msg=${e.message}")
                }
            }.getOrDefault(false)
        }
    }

    /** path가 FTP 경로인지 여부를 반환한다. */
    override fun isRemotePath(path: String): Boolean = path.startsWith("ftp://")

    /** 연결된 FTPClient로 경로의 파일 목록을 반환한다. 미연결 또는 실패 시 빈 목록을 반환한다. */
    override suspend fun listFiles(path: String): List<FileEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!client.isConnected || !isLoggedIn) {
                Log.w(TAG, "listFiles: 미연결 또는 미인증 path=$path")
                return@withContext emptyList()
            }
            val ftpPath = extractFtpPath(path)
            Log.d(TAG, "listFiles: server=$connectedServer ftpPath=$ftpPath")
            runCatching {
                val entries = client.listFiles(ftpPath)
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
                Log.d(TAG, "listFiles: success path=$path count=${entries.size}")
                entries
            }.onFailure { e ->
                Log.e(TAG, "listFiles: ${e::class.simpleName} path=$path msg=${e.message}")
            }.getOrDefault(emptyList())
        }
    }

    /** ftp://server/path 형식에서 FTP 서버 상의 절대 경로를 추출한다. */
    private fun extractFtpPath(path: String): String {
        val withoutScheme = path.removePrefix("ftp://")
        val slashIndex = withoutScheme.indexOf('/')
        return if (slashIndex == -1) "/" else withoutScheme.substring(slashIndex)
    }

    companion object {
        private const val TAG = "FtpFileSystem"
    }
}
