package com.todokanai.composepracticenew.data.ftp

import android.util.Log
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** 싱글톤 FTPClient를 유지하며 FTP 원격 파일 시스템에 접근한다. 한 번 연결 후 재사용한다. */
@Singleton
class FtpFileSystem @Inject constructor() {

    private val client = FTPClient()
    private var connectedServer: String? = null

    /**
     * FTP 서버에 연결하고 로그인한다.
     * 이미 연결된 경우 기존 연결을 해제 후 재연결한다.
     * @return 로그인 성공 여부
     */
    suspend fun connect(address: String, port: Int, userId: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val server = address.removePrefix("ftp://")
        Log.d(TAG, "connect: server=$server userId=$userId")
        runCatching {
            if (client.isConnected) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
            client.connectTimeout = 10_000
            client.connect(server, port)
            client.soTimeout = 15_000
            val loggedIn = client.login(userId, password)
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

    /** 서버 주소로부터 FTP 루트 경로 문자열을 생성한다. */
    fun buildRootPath(address: String): String =
        "ftp://${address.removePrefix("ftp://")}"

    /** path가 FTP 경로인지 여부를 반환한다. */
    fun isRemotePath(path: String): Boolean = path.startsWith("ftp://")

    /**
     * FTP 경로의 부모 경로를 반환한다.
     * 서버 루트(ftp://server)에서는 null을 반환한다.
     */
    fun getParent(path: String): String? {
        val withoutScheme = path.removePrefix("ftp://")
        if (!withoutScheme.contains('/')) return null
        return "ftp://" + withoutScheme.substringBeforeLast('/')
    }

    /** 연결된 FTPClient로 경로의 파일 목록을 반환한다. 미연결 또는 실패 시 빈 목록을 반환한다. */
    suspend fun listFiles(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        if (!client.isConnected) {
            Log.w(TAG, "listFiles: 연결되지 않음 path=$path")
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
