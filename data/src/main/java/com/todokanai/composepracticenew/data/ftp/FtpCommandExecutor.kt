package com.todokanai.composepracticenew.data.ftp

import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPConnectionClosedException
import javax.inject.Inject
import javax.inject.Singleton

/** FTP 파일 명령(조회·변경·삭제·생성)을 FtpConnectionState의 공유 클라이언트를 통해 실행한다. */
@Singleton
class FtpCommandExecutor @Inject constructor(
    private val cs: FtpConnectionState
) {

    /**
     * 주어진 원격 경로의 파일 목록을 반환한다. 미연결 시 빈 목록을 반환한다.
     * @param path ftp://server/path 형식의 절대 경로
     */
    suspend fun listFiles(path: String): List<FileEntry> {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return emptyList()
            extractFtpPath(path)
        }
        return cs.ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    cs.client.listFiles(ftpPath)
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
                    if (e is FTPConnectionClosedException) cs.markConnectionDropped()
                }.getOrDefault(emptyList())
            }
        }
    }

    /** 현재 FTP 작업 디렉터리의 절대 경로를 반환한다. 미연결 시 빈 문자열을 반환한다. */
    suspend fun getWorkingDirectory(): String {
        cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return ""
        }
        return runFtpQuery("") { cs.client.printWorkingDirectory() ?: "" }
    }

    /**
     * path로 FTP 작업 디렉터리를 변경한다. 미연결 시 false를 반환한다.
     * @param path 이동할 원격 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun changeDirectory(path: String): Boolean {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return false
            extractFtpPath(path)
        }
        return runFtpBoolean { cs.client.changeWorkingDirectory(ftpPath) }
    }

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다. 미연결 시 false를 반환한다.
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     */
    suspend fun rename(fromPath: String, toPath: String): Boolean {
        var fromFtpPath = ""
        var toFtpPath = ""
        cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return false
            fromFtpPath = extractFtpPath(fromPath)
            toFtpPath = extractFtpPath(toPath)
        }
        return runFtpBoolean { cs.client.rename(fromFtpPath, toFtpPath) }
    }

    /**
     * path의 파일을 원격 서버에서 삭제한다. 미연결 시 false를 반환한다.
     * @param path 삭제할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun deleteFile(path: String): Boolean {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return false
            extractFtpPath(path)
        }
        return runFtpBoolean { cs.client.deleteFile(ftpPath) }
    }

    /**
     * path에 새 원격 디렉터리를 생성한다. 미연결 시 false를 반환한다.
     * @param path 생성할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun makeDirectory(path: String): Boolean {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return false
            extractFtpPath(path)
        }
        return runFtpBoolean { cs.client.makeDirectory(ftpPath) }
    }

    /**
     * path의 빈 디렉터리를 원격 서버에서 삭제한다. 미연결 시 false를 반환한다.
     * @param path 삭제할 빈 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun removeDirectory(path: String): Boolean {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return false
            extractFtpPath(path)
        }
        return runFtpBoolean { cs.client.removeDirectory(ftpPath) }
    }

    /**
     * path 파일의 크기를 바이트 단위로 반환한다. 조회 실패 또는 미연결 시 -1을 반환한다.
     * @param path 크기를 조회할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun getFileSize(path: String): Long {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return -1L
            extractFtpPath(path)
        }
        return runFtpQuery(-1L) { cs.client.mlistFile(ftpPath)?.size ?: -1L }
    }

    /**
     * path 파일의 최종 수정 시각을 FTP MDTM 형식 문자열로 반환한다. 조회 실패 또는 미연결 시 빈 문자열을 반환한다.
     * @param path 수정 시각을 조회할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun getModificationTime(path: String): String {
        val ftpPath = cs.stateMutex.withLock {
            if (!cs.client.isConnected || !cs.isLoggedIn) return ""
            extractFtpPath(path)
        }
        return runFtpQuery("") { cs.client.getModificationTime(ftpPath) ?: "" }
    }

    /**
     * path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다. 미연결 시 false를 반환한다.
     * collectRemoteTree로 전체 트리를 수집한 뒤 reversed()로 리프부터 삭제해 RMD 빈 디렉터리 조건을 충족한다.
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun removeDirectoryRecursive(path: String): Boolean =
        withContext(cs.ftpIoJob + Dispatchers.IO) {
            cs.stateMutex.withLock {
                if (!cs.client.isConnected || !cs.isLoggedIn) return@withContext false
            }
            val allEntries = collectRemoteTree(path)
            var hasFailure = false
            for (entry in allEntries.reversed()) {
                val success = if (entry.isDirectory) {
                    runFtpBoolean { cs.client.removeDirectory(extractFtpPath(entry.path)) }
                } else {
                    runFtpBoolean { cs.client.deleteFile(extractFtpPath(entry.path)) }
                }
                if (!success) hasFailure = true
            }
            if (hasFailure) return@withContext false
            runFtpBoolean { cs.client.removeDirectory(extractFtpPath(path)) }
        }

    /** remoteDirPath 하위의 모든 항목을 깊이 우선으로 평탄화한 목록을 반환한다. 디렉터리는 자신의 자식보다 앞에 온다. */
    internal suspend fun collectRemoteTree(remoteDirPath: String): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        val entries = listFiles(remoteDirPath)
        for (entry in entries) {
            result.add(entry)
            if (entry.isDirectory) {
                result.addAll(collectRemoteTree(entry.path))
            }
        }
        return result
    }

    /** ioMutex 확보 후 IO 스레드에서 FTP 명령을 실행하며 접속 끊김 시 markConnectionDropped를 호출한다. */
    private suspend fun runFtpBoolean(block: () -> Boolean): Boolean =
        cs.ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching(block)
                    .onFailure { e -> if (e is FTPConnectionClosedException) cs.markConnectionDropped() }
                    .getOrDefault(false)
            }
        }

    /** ioMutex 확보 후 IO 스레드에서 FTP 조회 명령을 실행한다. 실패 시 default를 반환한다. */
    private suspend fun <T> runFtpQuery(default: T, block: () -> T): T =
        cs.ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching(block).getOrDefault(default)
            }
        }
}
