package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 명령(목록 조회·이름변경·삭제·생성)을 FtpSession을 통해 실행한다.
 * 목록 조회([listFiles])를 제외한 명령은 실패를 예외로 알리며, 연결이 없거나 끊기면 [RemoteConnectionLostException]을 던진다.
 * @param session 명령을 실행할 FTPClient와 잠금을 소유한 FTP 세션
 */
@Singleton
class FtpCommandExecutor @Inject constructor(
    private val session: FtpSession
) {

    /**
     * 주어진 원격 경로의 파일 목록을 반환한다. 미연결이거나 조회에 실패하면 빈 목록을 반환한다.
     * 화면 목록 표시용이며, 실패와 빈 폴더를 구분해야 하는 작업은 [collectRemoteTree]를 쓴다.
     * @param path ftp://server/path 형식의 절대 경로
     */
    suspend fun listFiles(path: String): List<FileEntry> =
        try {
            listFilesOrThrow(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다.
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     */
    suspend fun rename(fromPath: String, toPath: String) {
        val fromFtpPath = extractFtpPath(fromPath)
        val toFtpPath = extractFtpPath(toPath)
        runFtpCommand("RENAME") { it.rename(fromFtpPath, toFtpPath) }
    }

    /**
     * path의 파일을 원격 서버에서 삭제한다.
     * @param path 삭제할 원격 파일의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun deleteFile(path: String) {
        val ftpPath = extractFtpPath(path)
        runFtpCommand("DELE") { it.deleteFile(ftpPath) }
    }

    /**
     * path에 새 원격 디렉터리를 생성한다.
     * @param path 생성할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun makeDirectory(path: String) {
        val ftpPath = extractFtpPath(path)
        runFtpCommand("MKD") { it.makeDirectory(ftpPath) }
    }

    /**
     * path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다.
     * collectRemoteTree로 전체 트리를 수집한 뒤 reversed()로 리프부터 삭제해 RMD 빈 디렉터리 조건을 충족한다.
     * 일부 하위 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 던지며, 이때 path 자체는 지우지 않는다.
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun removeDirectoryRecursive(path: String) {
        val allEntries = collectRemoteTree(path)
        forEachCollectingFailures(allEntries.reversed(), "하위 항목 삭제 실패", nameOf = { it.path.removePrefix(path).trimStart('/') }) { entry ->
            val entryFtpPath = extractFtpPath(entry.path)
            if (entry.isDirectory) runFtpCommand("RMD") { it.removeDirectory(entryFtpPath) }
            else runFtpCommand("DELE") { it.deleteFile(entryFtpPath) }
        }
        val ftpPath = extractFtpPath(path)
        runFtpCommand("RMD") { it.removeDirectory(ftpPath) }
    }

    /**
     * remoteDirPath 하위의 모든 항목을 깊이 우선으로 평탄화한 목록을 반환한다. 디렉터리는 자신의 자식보다 앞에 온다.
     * 어느 하위 디렉터리라도 조회에 실패하면 일부만 담긴 목록 대신 예외를 던진다.
     */
    internal suspend fun collectRemoteTree(remoteDirPath: String): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        val entries = listFilesOrThrow(remoteDirPath)
        for (entry in entries) {
            result.add(entry)
            if (entry.isDirectory) {
                result.addAll(collectRemoteTree(entry.path))
            }
        }
        return result
    }

    /**
     * 주어진 원격 경로의 파일 목록을 조회한다. FTPClient.listFiles는 데이터 연결을 열지 못해도 빈 배열을 돌려주므로
     * 응답 코드로 실패를 판별해 예외로 알린다.
     * @param path ftp://server/path 형식의 절대 경로
     * @return 원격 경로의 파일 목록
     */
    private suspend fun listFilesOrThrow(path: String): List<FileEntry> {
        val ftpPath = extractFtpPath(path)
        return session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                val files = client.listFiles(ftpPath)
                if (!FTPReply.isPositiveCompletion(client.replyCode)) throw replyFailure(client, "LIST")
                files?.filter { it.name != "." && it.name != ".." }
                    ?.map { file ->
                        FileEntry(
                            name = file.name,
                            path = path.trimEnd('/') + "/" + file.name,
                            isDirectory = file.isDirectory,
                            size = file.size,
                            lastModified = file.timestamp?.timeInMillis ?: 0L
                        )
                    } ?: emptyList()
            }
        }
    }

    /**
     * 세션 잠금 확보 후 IO 스레드에서 성공 여부를 반환하는 FTP 명령을 실행하고, 실패하면 서버 응답을 담아 예외를 던진다.
     * @param commandName 실패 메시지에 표시할 명령 이름
     * @param block 실행할 FTP 명령. 성공하면 true를 반환한다
     */
    private suspend fun runFtpCommand(commandName: String, block: (FTPClient) -> Boolean) {
        session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (!block(client)) throw replyFailure(client, commandName)
            }
        }
    }

    /**
     * 직전 FTP 명령의 서버 응답을 담은 실패 예외를 만든다.
     * @param client 응답을 받은 FTPClient
     * @param commandName 실패한 명령 이름
     * @return 서버 응답을 메시지로 담은 IOException
     */
    private fun replyFailure(client: FTPClient, commandName: String): IOException =
        IOException("$commandName 실패: ${client.replyString.trim()}")
}
