package com.todokanai.composepracticenew.data.ftp

import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 명령(목록 조회·이름변경·삭제·생성)을 FtpSession을 통해 실행한다.
 * @param session 명령을 실행할 FTPClient와 잠금을 소유한 FTP 세션
 */
@Singleton
class FtpCommandExecutor @Inject constructor(
    private val session: FtpSession
) {

    /**
     * 주어진 원격 경로의 파일 목록을 반환한다. 미연결 시 빈 목록을 반환한다.
     * @param path ftp://server/path 형식의 절대 경로
     */
    suspend fun listFiles(path: String): List<FileEntry> {
        if (!session.isReady()) return emptyList()
        val ftpPath = extractFtpPath(path)
        return session.withClient { client ->
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
                    if (e is FTPConnectionClosedException) session.markConnectionDropped()
                }.getOrDefault(emptyList())
            }
        }
    }

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다. 미연결 시 false를 반환한다.
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     */
    suspend fun rename(fromPath: String, toPath: String): Boolean {
        if (!session.isReady()) return false
        val fromFtpPath = extractFtpPath(fromPath)
        val toFtpPath = extractFtpPath(toPath)
        return runFtpBoolean { it.rename(fromFtpPath, toFtpPath) }
    }

    /**
     * path의 파일을 원격 서버에서 삭제한다. 미연결 시 false를 반환한다.
     * @param path 삭제할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun deleteFile(path: String): Boolean {
        if (!session.isReady()) return false
        val ftpPath = extractFtpPath(path)
        return runFtpBoolean { it.deleteFile(ftpPath) }
    }

    /**
     * path에 새 원격 디렉터리를 생성한다. 미연결 시 false를 반환한다.
     * @param path 생성할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun makeDirectory(path: String): Boolean {
        if (!session.isReady()) return false
        val ftpPath = extractFtpPath(path)
        return runFtpBoolean { it.makeDirectory(ftpPath) }
    }

    /**
     * path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다. 미연결 시 false를 반환한다.
     * collectRemoteTree로 전체 트리를 수집한 뒤 reversed()로 리프부터 삭제해 RMD 빈 디렉터리 조건을 충족한다.
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun removeDirectoryRecursive(path: String): Boolean =
        withContext(session.ioContext()) {
            if (!session.isReady()) return@withContext false
            val allEntries = collectRemoteTree(path)
            var hasFailure = false
            for (entry in allEntries.reversed()) {
                val entryFtpPath = extractFtpPath(entry.path)
                val success = if (entry.isDirectory) {
                    runFtpBoolean { it.removeDirectory(entryFtpPath) }
                } else {
                    runFtpBoolean { it.deleteFile(entryFtpPath) }
                }
                if (!success) hasFailure = true
            }
            if (hasFailure) return@withContext false
            runFtpBoolean { it.removeDirectory(extractFtpPath(path)) }
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

    /** 세션 잠금 확보 후 IO 스레드에서 FTP 명령을 실행하며 접속 끊김 시 markConnectionDropped를 호출한다. */
    private suspend fun runFtpBoolean(block: (FTPClient) -> Boolean): Boolean =
        session.withClient { client ->
            withContext(Dispatchers.IO) {
                runCatching { block(client) }
                    .onFailure { e -> if (e is FTPConnectionClosedException) session.markConnectionDropped() }
                    .getOrDefault(false)
            }
        }
}
