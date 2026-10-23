package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingCompleteness
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.FetchedListing
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ProtocolCommandEvent
import org.apache.commons.net.ProtocolCommandListener
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 명령(목록 조회·이름변경·삭제·생성)을 FtpSession을 통해 실행한다.
 * 모든 명령은 실패를 결과로 알리며, 연결이 없거나 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * 서버 응답의 해석은 [FtpReplyRules]에 맡긴다.
 * @param session 명령을 실행할 FTPClient와 잠금을 소유한 FTP 세션
 */
@Singleton
class FtpCommandExecutor @Inject constructor(
    private val session: FtpSession
) {

    /**
     * 화면에 보여줄 원격 경로의 파일 목록을 조회한다.
     * @param path ftp://server/path 형식의 절대 경로
     * @return 원격 경로의 파일 목록. 빈 디렉터리인지 확인할 수 없는 응답이면 [ListingCompleteness.UNCONFIRMED]인 빈 목록이다
     */
    suspend fun listFiles(path: String): IoResult<FetchedListing> = list(path, FtpListingPurpose.BROWSE)

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다.
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     * @return 명령 결과
     */
    suspend fun rename(fromPath: String, toPath: String): IoResult<Unit> {
        val fromFtpPath = extractFtpPath(fromPath)
        val toFtpPath = extractFtpPath(toPath)
        return runFtpCommand(FtpCommand.RENAME) { it.rename(fromFtpPath, toFtpPath) }
    }

    /**
     * path의 파일을 원격 서버에서 삭제한다.
     * @param path 삭제할 원격 파일의 절대 경로 (ftp://server/path 형식)
     * @return 명령 결과
     */
    suspend fun deleteFile(path: String): IoResult<Unit> {
        val ftpPath = extractFtpPath(path)
        return runFtpCommand(FtpCommand.DELE) { it.deleteFile(ftpPath) }
    }

    /**
     * path에 새 원격 디렉터리를 생성한다.
     * @param path 생성할 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @return 명령 결과
     */
    suspend fun makeDirectory(path: String): IoResult<Unit> {
        val ftpPath = extractFtpPath(path)
        return runFtpCommand(FtpCommand.MKD) { it.makeDirectory(ftpPath) }
    }

    /**
     * path의 디렉터리와 모든 하위 항목을 재귀적으로 삭제한다.
     * collectRemoteTree로 전체 트리를 수집한 뒤 reversed()로 리프부터 삭제해 RMD 빈 디렉터리 조건을 충족한다.
     * 일부 하위 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패를 모아 알리며, 이때 path 자체는 지우지 않는다.
     * 실패한 항목을 품은 상위 디렉터리는 비울 수 없으므로 RMD를 보내지도, 실패로 세지도 않는다(GNU rm과 같은 방식).
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @return 삭제 결과. 하위 항목 일부가 실패하면 [IoResult.PartiallyFailed]
     */
    suspend fun removeDirectoryRecursive(path: String): IoResult<Unit> {
        val allEntries = collectRemoteTree(path).valueOr { return it }
        forEachCollectingFailures(
            allEntries.reversed(),
            "하위 항목 삭제 실패",
            nameOf = { relativeRemotePath(it.path, path) },
            isBlockedBy = { entry, failed -> entry.isDirectory && isRemoteAncestorOf(entry.path, failed.path) }
        ) { entry ->
            val entryFtpPath = extractFtpPath(entry.path)
            if (entry.isDirectory) runFtpCommand(FtpCommand.RMD) { it.removeDirectory(entryFtpPath) }
            else runFtpCommand(FtpCommand.DELE) { it.deleteFile(entryFtpPath) }
        }.valueOr { return it }
        val ftpPath = extractFtpPath(path)
        return runFtpCommand(FtpCommand.RMD) { it.removeDirectory(ftpPath) }
    }

    /**
     * remoteDirPath 하위의 모든 항목을 깊이 우선으로 평탄화한 목록을 반환한다. 디렉터리는 자신의 자식보다 앞에 온다.
     * 어느 하위 디렉터리라도 조회에 실패하면 일부만 담긴 목록 대신 그 실패를 돌려준다.
     * @param remoteDirPath 수집할 원격 디렉터리 경로 (ftp://server/path 형식)
     * @return 하위 항목 전체 목록, 또는 조회 실패
     */
    internal suspend fun collectRemoteTree(remoteDirPath: String): IoResult<List<FileEntry>> {
        val result = mutableListOf<FileEntry>()
        // 트리 수집은 확인되지 않은 빈 목록을 만들지 않는다 — 그런 응답은 거부로 해석한다([FtpReplyRules.listingReplyOf])
        for (entry in list(remoteDirPath, FtpListingPurpose.TREE).valueOr { return it }.entries) {
            result.add(entry)
            if (entry.isDirectory) result.addAll(collectRemoteTree(entry.path).valueOr { return it })
        }
        return IoResult.Success(result)
    }

    /**
     * 원격 경로의 파일 목록을 조회한다. 화면 목록([listFiles])과 트리 수집([collectRemoteTree])이 같은 조회를 써서 둘이 보는 항목이 같고,
     * 거부 응답을 빈 목록으로 볼지만 [purpose]에 따라 [FtpReplyRules.listingReplyOf]가 정한다.
     * 서버가 FEAT에 MLST를 알리면(RFC 3659 §7.8: MLSD 지원 포함) MLSD로, 아니면 LIST로 조회한다.
     * MLSD는 없는 경로·권한 없음을 550으로 정하고 있어 빈 폴더와 실패가 구분된다.
     * @param path ftp://server/path 형식의 절대 경로
     * @param purpose 조회 목적
     * @return 원격 경로의 파일 목록과 그 목록이 확인된 목록인지 여부
     */
    private suspend fun list(path: String, purpose: FtpListingPurpose): IoResult<FetchedListing> {
        val ftpPath = extractFtpPath(path)
        return session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (FtpListingFormat.usesMlsd(client)) listWithMlsd(client, ftpPath, path, purpose)
                else listWithList(client, ftpPath, path, purpose)
            }
        }
    }

    /**
     * LIST로 목록을 조회한다. FTPClient.listFiles는 데이터 연결을 열지 못해도 빈 배열을 돌려주므로 응답으로 결과를 판별한다.
     * 목록 해석기는 연결할 때 [FtpListingFormat.isReadable]로 확인했으므로, 이 연결에서는 해석기를 고르지 못해 실패하지 않는다.
     * 숨김 항목을 담는 `-a` 옵션은 연결할 때 [FtpListingFormat.configureHiddenListing]이 [client]에 정해 두었다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param purpose 조회 목적
     * @return 디렉터리의 항목 목록, 또는 서버가 거부한 실패
     */
    private fun listWithList(client: FTPClient, ftpPath: String, path: String, purpose: FtpListingPurpose): IoResult<FetchedListing> {
        val (files, transferStarted) = watchingDataTransfer(client) { client.listFiles(ftpPath) }
        return listingOutcome(FtpCommand.LIST, purpose, client, transferStarted, path) {
            files?.filter { it.name != "." && it.name != ".." }.orEmpty()
        }
    }

    /**
     * MLSD로 목록을 조회한다. mlistDir도 데이터 연결을 열지 못하면 빈 배열을 돌려주므로 응답으로 결과를 판별한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param purpose 조회 목적
     * @return 디렉터리의 항목 목록 (자신·상위 디렉터리 항목 제외), 또는 서버가 거부한 실패
     */
    private fun listWithMlsd(client: FTPClient, ftpPath: String, path: String, purpose: FtpListingPurpose): IoResult<FetchedListing> {
        val (files, transferStarted) = watchingDataTransfer(client) { client.mlistDir(ftpPath) }
        return listingOutcome(FtpCommand.MLSD, purpose, client, transferStarted, path) {
            files?.filterNot { isSelfOrParentEntry(it) }.orEmpty()
        }
    }

    /**
     * 목록 조회 [list]를 실행하며 서버가 예비 응답(1xx)을 보냈는지 함께 돌려준다. 목록 조회에서 1xx는 데이터 연결을 열어 목록 전송을 시작했다는 뜻이다.
     * commons-net의 목록 조회는 데이터 연결을 열었는지 알려주지 않으므로 응답 이벤트로 판별한다.
     * @param client 잠금을 확보한 FTPClient
     * @param list 실행할 목록 조회
     * @return 조회한 항목과 목록 전송 시작 여부
     */
    private inline fun watchingDataTransfer(client: FTPClient, list: () -> Array<FTPFile>?): Pair<Array<FTPFile>?, Boolean> {
        val watch = PreliminaryReplyWatch()
        client.addProtocolCommandListener(watch)
        try {
            return list() to watch.received
        } finally {
            client.removeProtocolCommandListener(watch)
        }
    }

    /**
     * 목록 조회의 마지막 응답을 [FtpReplyRules]로 해석해 결과로 만든다.
     * 빈 디렉터리로 볼 거부 응답은 실제로 빈 디렉터리인지 알 수 없으므로 확인되지 않은 빈 목록이 된다.
     * @param command 보낸 목록 조회 명령
     * @param purpose 조회 목적
     * @param client 응답을 받은 FTPClient
     * @param dataTransferStarted 서버가 목록 전송을 시작했는지 여부
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param files 조회를 마쳤을 때 받은 항목
     * @return 조회 결과
     */
    private fun listingOutcome(
        command: FtpCommand,
        purpose: FtpListingPurpose,
        client: FTPClient,
        dataTransferStarted: Boolean,
        path: String,
        files: () -> List<FTPFile>
    ): IoResult<FetchedListing> =
        when (FtpReplyRules.listingReplyOf(command, purpose, client.replyCode, dataTransferStarted)) {
            FtpListingReply.COMPLETED -> IoResult.Success(FetchedListing.complete(files().map { toFileEntry(it, path) }))
            FtpListingReply.NO_ENTRIES -> IoResult.Success(FetchedListing(emptyList(), ListingCompleteness.UNCONFIRMED))
            FtpListingReply.REFUSED -> FtpReplyRules.refusal(command, client)
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
     * FTP 목록 항목 하나를 domain 항목으로 바꾼다.
     * @param file 변환할 FTP 목록 항목
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 변환된 항목
     */
    private fun toFileEntry(file: FTPFile, path: String): FileEntry = FileEntry(
        name = file.name,
        path = path.trimEnd('/') + "/" + file.name,
        isDirectory = file.isDirectory,
        size = file.size,
        lastModified = file.timestamp?.timeInMillis ?: 0L
    )

    /**
     * 세션 잠금 확보 후 IO 스레드에서 성공 여부를 반환하는 FTP 명령을 실행하고, 실패하면 서버 응답을 담은 실패를 돌려준다.
     * 성공 판정은 명령마다 성공 응답을 아는 commons-net의 반환값을 따른다.
     * @param command 실행할 명령 — 실패 메시지에 쓴다
     * @param block 실행할 FTP 명령. 성공하면 true를 반환한다
     * @return 명령 결과
     */
    private suspend fun runFtpCommand(command: FtpCommand, block: (FTPClient) -> Boolean): IoResult<Unit> =
        session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (block(client)) IoResult.Done else FtpReplyRules.refusal(command, client)
            }
        }

    /** 명령 중 서버가 예비 응답(1xx)을 보냈는지 기록하는 응답 이벤트 수신기. */
    private class PreliminaryReplyWatch : ProtocolCommandListener {
        /** 예비 응답을 받았는지 여부. */
        var received = false
            private set

        override fun protocolCommandSent(event: ProtocolCommandEvent) = Unit

        override fun protocolReplyReceived(event: ProtocolCommandEvent) {
            if (FTPReply.isPositivePreliminary(event.replyCode)) received = true
        }
    }
}
