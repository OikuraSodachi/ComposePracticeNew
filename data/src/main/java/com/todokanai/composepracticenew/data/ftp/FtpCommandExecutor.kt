package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.util.Locale
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
     * 실패한 항목을 품은 상위 디렉터리는 비울 수 없으므로 RMD를 보내지도, 실패로 세지도 않는다(GNU rm과 같은 방식).
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    suspend fun removeDirectoryRecursive(path: String) {
        val allEntries = collectRemoteTree(path)
        forEachCollectingFailures(
            allEntries.reversed(),
            "하위 항목 삭제 실패",
            nameOf = { relativeRemotePath(it.path, path) },
            isBlockedBy = { entry, failed -> entry.isDirectory && isRemoteAncestorOf(entry.path, failed.path) }
        ) { entry ->
            val entryFtpPath = extractFtpPath(entry.path)
            if (entry.isDirectory) runFtpCommand("RMD") { it.removeDirectory(entryFtpPath) }
            else runFtpCommand("DELE") { it.deleteFile(entryFtpPath) }
        }
        val ftpPath = extractFtpPath(path)
        runFtpCommand("RMD") { it.removeDirectory(ftpPath) }
    }

    /**
     * remoteDirPath 하위의 모든 항목을 깊이 우선으로 평탄화한 목록을 반환한다. 디렉터리는 자신의 자식보다 앞에 온다.
     * 폴더 다운로드·삭제 같은 작업용이므로 빈 폴더와 조회 실패를 구분할 수 있는 [listForOperationOrThrow]로 조회하며,
     * 어느 하위 디렉터리라도 조회에 실패하면 일부만 담긴 목록 대신 예외를 던진다.
     */
    internal suspend fun collectRemoteTree(remoteDirPath: String): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        val entries = listForOperationOrThrow(remoteDirPath)
        for (entry in entries) {
            result.add(entry)
            if (entry.isDirectory) {
                result.addAll(collectRemoteTree(entry.path))
            }
        }
        return result
    }

    /**
     * 화면 목록용으로 주어진 원격 경로의 파일 목록을 LIST로 조회한다. 실패는 응답 코드로 판별해 예외로 알린다.
     * @param path ftp://server/path 형식의 절대 경로
     * @return 원격 경로의 파일 목록
     */
    private suspend fun listFilesOrThrow(path: String): List<FileEntry> {
        val ftpPath = extractFtpPath(path)
        return session.withConnectedClient { client ->
            withContext(Dispatchers.IO) { listWithList(client, ftpPath, path) }
        }
    }

    /**
     * 작업용으로 주어진 원격 경로의 파일 목록을 조회한다. 서버가 FEAT에 MLST를 알리면(RFC 3659 §7.8: MLSD 지원 포함) MLSD로,
     * 아니면 LIST로 조회한다. MLSD는 없는 경로·권한 없음을 550으로 정하고 있어 빈 폴더와 실패가 구분된다.
     * LIST는 빈 폴더에 오류 응답을 주는 비표준 서버를 구분할 수 없으므로, 조용히 빠뜨리지 않도록 오류 응답을 실패로 본다.
     * @param path ftp://server/path 형식의 절대 경로
     * @return 원격 경로의 파일 목록
     */
    private suspend fun listForOperationOrThrow(path: String): List<FileEntry> {
        val ftpPath = extractFtpPath(path)
        return session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (client.hasFeature(FTPCmd.MLST)) listWithMlsd(client, ftpPath, path)
                else listWithList(client, ftpPath, path)
            }
        }
    }

    /**
     * LIST로 목록을 조회한다. FTPClient.listFiles는 데이터 연결을 열지 못해도 빈 배열을 돌려주므로 응답 코드로 실패를 판별한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 디렉터리의 항목 목록
     */
    private fun listWithList(client: FTPClient, ftpPath: String, path: String): List<FileEntry> {
        val files = client.listFiles(ftpPath)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) throw replyFailure(client, "LIST")
        return toFileEntries(files?.filter { it.name != "." && it.name != ".." }.orEmpty(), path)
    }

    /**
     * MLSD로 목록을 조회한다. mlistDir도 데이터 연결을 열지 못하면 빈 배열을 돌려주므로 응답 코드로 실패를 판별한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 디렉터리의 항목 목록 (자신·상위 디렉터리 항목 제외)
     */
    private fun listWithMlsd(client: FTPClient, ftpPath: String, path: String): List<FileEntry> {
        val files = client.mlistDir(ftpPath)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) throw replyFailure(client, "MLSD")
        return toFileEntries(files?.filterNot { isSelfOrParentEntry(it) }.orEmpty(), path)
    }

    /**
     * MLSD 항목이 나열한 디렉터리 자신(type=cdir)이나 상위 디렉터리(type=pdir)인지 판별한다.
     * commons-net은 두 종류를 일반 디렉터리로 파싱하고, 서버에 따라 이름이 "."이 아닌 전체 경로로 오므로 원본 줄의 type 사실로 판별한다.
     * 걸러내지 않으면 트리 수집이 자기 자신을 하위 디렉터리로 보고 끝없이 재귀한다.
     * @param file MLSD로 받은 항목
     * @return 자신·상위 디렉터리 항목이면 true
     */
    private fun isSelfOrParentEntry(file: FTPFile): Boolean {
        val facts = file.rawListing?.substringBefore(' ')?.lowercase(Locale.ROOT) ?: return false
        return facts.split(';').any { it == "type=cdir" || it == "type=pdir" }
    }

    /**
     * [ancestorPath]가 [path]를 하위 항목으로 품는 원격 디렉터리 경로인지 판별한다.
     * @param ancestorPath 상위 후보 디렉터리 경로
     * @param path 하위 후보 항목 경로
     * @return [path]가 [ancestorPath] 아래에 있으면 true
     */
    private fun isRemoteAncestorOf(ancestorPath: String, path: String): Boolean = path.startsWith(ancestorPath.trimEnd('/') + "/")

    /**
     * FTP 목록 항목을 domain 항목으로 바꾼다.
     * @param files 변환할 FTP 목록 항목
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 변환된 항목 목록
     */
    private fun toFileEntries(files: List<FTPFile>, path: String): List<FileEntry> = files.map { file ->
        FileEntry(
            name = file.name,
            path = path.trimEnd('/') + "/" + file.name,
            isDirectory = file.isDirectory,
            size = file.size,
            lastModified = file.timestamp?.timeInMillis ?: 0L
        )
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
     * 직전 FTP 명령의 서버 오류 응답을 담은 실패 예외를 만든다. 응답을 정상적으로 읽은 실패이므로 세션은 계속 쓸 수 있다.
     * @param client 응답을 받은 FTPClient
     * @param commandName 실패한 명령 이름
     * @return 서버 응답을 메시지로 담은 항목 실패 예외
     */
    private fun replyFailure(client: FTPClient, commandName: String): FtpItemFailure =
        FtpItemFailure("$commandName 실패: ${client.replyString.trim()}")
}
