package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.map
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 목록 조회 결과와, 서버가 알려준 식별값(MLSD `unique`). 트리 수집이 순환을 판별하는 데 쓴다.
 * @param entries 조회한 항목
 * @param identities 항목 경로 → 식별값. 서버가 식별값을 알려준 항목만 담는다
 * @param selfIdentity 나열한 디렉터리 자신의 식별값. 모르면 null
 */
private data class IdentifiedListing(val entries: List<FileEntry>, val identities: Map<String, String>, val selfIdentity: String?)

/**
 * 서버에게 받은 목록 항목 하나와 그 식별값.
 * @param file 받은 항목
 * @param identity 서버가 알려준 식별값. 없으면 null
 */
private data class ReceivedEntry(val file: FTPFile, val identity: String?)

/**
 * 서버에게 받은 목록에서 나열한 디렉터리 자신·상위 항목을 걸러낸 항목들.
 * @param entries 디렉터리 안의 항목
 * @param selfIdentity 나열한 디렉터리 자신의 식별값. 모르면 null
 */
private data class ReceivedListing(val entries: List<ReceivedEntry>, val selfIdentity: String?)

/**
 * 트리 수집이 들어가 있는 디렉터리 하나.
 * @param listed 그 디렉터리의 목록
 * @param identities 그 디렉터리의 식별값(상위 목록의 항목과 자신 항목이 알려준 값). 모르면 비어 있다
 */
private class DirectoryVisit(val listed: IdentifiedListing, val identities: Set<String>) {
    /** 아직 수집하지 않은 항목. */
    val remaining: Iterator<FileEntry> = listed.entries.iterator()
}

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
     * @return 원격 경로의 파일 목록, 또는 서버가 거부한 실패
     */
    suspend fun listFiles(path: String): IoResult<List<FileEntry>> = list(path, withIdentities = false).map { it.entries }

    /**
     * fromPath를 toPath로 이름 변경 또는 이동한다.
     * 알려진 문제(방치하기로 결정): 서버가 목록에서 숨긴 항목이 toPath에 있으면 충돌 검사에 보이지 않고, RNTO가 기존 파일을 바꾸는 서버(vsftpd 등)에서는
     * 묻지 않고 덮어쓴다([identifiedListing] 참고).
     * @param fromPath 원본 경로 (ftp://server/path 형식)
     * @param toPath 변경할 경로 (ftp://server/path 형식)
     * @return 명령 결과
     */
    suspend fun rename(fromPath: String, toPath: String): IoResult<Unit> {
        val fromFtpPath = extractFtpPath(fromPath)
        val toFtpPath = extractFtpPath(toPath)
        return runFtpCommand(FtpCommand.RENAME, leavingTarget(fromFtpPath, outside = parentFtpPath(fromFtpPath))) {
            it.rename(fromFtpPath, toFtpPath)
        }
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
     * 트리 수집이 세션을 하위 폴더 안에 남기므로, RMD는 세션이 지울 폴더 안에 있으면 먼저 [path]의 상위 폴더로 나간 뒤 보낸다.
     * 알려진 문제(방치하기로 결정): 트리에 서버가 숨긴 항목이 빠질 수 있어([collectRemoteTree]), 그런 항목이 있으면 보이는 항목을 모두 지운 뒤에야
     * RMD 거부로 실패를 알게 된다. 이 경우 거부 메시지에 숨긴 항목이 남았을 가능성을 덧붙인다([FtpReplyRules.HIDDEN_ENTRIES_NOTE]).
     * @param path 삭제할 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @return 삭제 결과. 하위 항목 일부가 실패하면 [IoResult.PartiallyFailed]
     */
    suspend fun removeDirectoryRecursive(path: String): IoResult<Unit> {
        val allEntries = collectRemoteTree(path).valueOr { return it }
        val ftpPath = extractFtpPath(path)
        val outside = parentFtpPath(ftpPath)
        forEachCollectingFailures(
            allEntries.reversed(),
            "하위 항목 삭제 실패",
            nameOf = { relativeRemotePath(it.path, path) },
            isBlockedBy = { entry, failed -> entry.isDirectory && isRemoteAncestorOf(entry.path, failed.path) }
        ) { entry ->
            val entryFtpPath = extractFtpPath(entry.path)
            if (entry.isDirectory) runFtpCommand(FtpCommand.RMD, leavingTarget(entryFtpPath, outside)) { it.removeDirectory(entryFtpPath) }
            else runFtpCommand(FtpCommand.DELE) { it.deleteFile(entryFtpPath) }
        }.valueOr { return it }
        return runFtpCommand(FtpCommand.RMD, leavingTarget(ftpPath, outside)) { it.removeDirectory(ftpPath) }
    }

    /**
     * remoteDirPath 하위의 모든 항목을 깊이 우선으로 평탄화한 목록을 반환한다. 디렉터리는 자신의 자식보다 앞에 온다.
     * 어느 하위 디렉터리라도 조회에 실패하거나 내려갈 수 없으면([descentRefusal]) 일부만 담긴 목록 대신 그 실패를 돌려준다.
     * 목록이 상위 디렉터리를 하위로 보여줘도(디렉터리로 보고된 링크, 서버 오류) 수집은 끝난다.
     * 깊이는 경로 한도까지 수천 단계가 될 수 있어, 재귀 대신 들어가 있는 디렉터리를 쌓아 두고 거쳐 온 식별값을 한 집합에서 넣고 뺀다 —
     * 스레드 스택 깊이에 기대지 않고, 메모리가 깊이에 비례한다.
     * 시작 디렉터리의 식별값은 그 목록의 자신 항목(MLSD `type=cdir`)에서 얻는다.
     * 알려진 문제(방치하기로 결정): 각 목록은 서버가 드러내는 항목뿐이라([identifiedListing]) 서버가 숨긴 항목은 트리에서 빠지지만, 수집은 성공으로 끝난다.
     * 그래서 폴더 다운로드는 숨긴 항목 없이 완료로 보고되고, 재귀 삭제는 보이는 항목을 지운 뒤 RMD에서 실패한다([removeDirectoryRecursive]).
     * @param remoteDirPath 수집할 원격 디렉터리 경로 (ftp://server/path 형식)
     * @return 하위 항목 전체 목록, 또는 조회 실패
     */
    internal suspend fun collectRemoteTree(remoteDirPath: String): IoResult<List<FileEntry>> {
        val result = mutableListOf<FileEntry>()
        // 거쳐 온 디렉터리의 식별값 — 이미 있는 값이면 내려가지 않으므로 같은 값이 두 번 들어가지 않아, 그 디렉터리를 나올 때 빼도 된다
        val ancestorIdentities = HashSet<String>()
        val pending = ArrayDeque<DirectoryVisit>()
        pending.addLast(enterDirectory(remoteDirPath, entryIdentity = null, ancestorIdentities).valueOr { return it })
        while (pending.isNotEmpty()) {
            val visit = pending.last()
            if (!visit.remaining.hasNext()) {
                pending.removeLast()
                ancestorIdentities.removeAll(visit.identities)
                continue
            }
            val entry = visit.remaining.next()
            result.add(entry)
            if (!entry.isDirectory) continue
            val identity = visit.listed.identities[entry.path]
            descentRefusal(entry.path, identity, ancestorIdentities)?.let { return it }
            pending.addLast(enterDirectory(entry.path, identity, ancestorIdentities).valueOr { return it })
        }
        return IoResult.Success(result)
    }

    /**
     * 트리 수집이 [dirPath]로 들어간다. 목록을 조회하고, 그 디렉터리의 식별값을 거쳐 온 식별값에 더한다.
     * 식별값은 상위 목록의 항목이 알려준 값과 그 디렉터리 목록의 자신 항목(MLSD `type=cdir`)이 알려준 값 둘 다 쓴다 —
     * 항목에는 링크 자신의 값을, 자신 항목에는 링크 대상의 값을 보내는 서버에서도 순환을 놓치지 않도록, 자신 항목의 값도 거쳐 온 식별값과 비교한다.
     * @param dirPath 들어갈 원격 디렉터리 경로 (ftp://server/path 형식)
     * @param entryIdentity 상위 목록의 항목이 알려준 이 디렉터리의 식별값. 모르거나 시작 디렉터리면 null. [ancestorIdentities]에 없는 값이어야 한다
     * @param ancestorIdentities 거쳐 온 디렉터리의 식별값. 이 디렉터리의 식별값이 더해진다
     * @return 들어간 디렉터리. 조회에 실패했거나 자신 항목의 식별값이 거쳐 온 디렉터리와 같으면 그 실패
     */
    private suspend fun enterDirectory(
        dirPath: String,
        entryIdentity: String?,
        ancestorIdentities: MutableSet<String>
    ): IoResult<DirectoryVisit> {
        val listed = list(dirPath, withIdentities = true).valueOr { return it }
        if (listed.selfIdentity != null && listed.selfIdentity in ancestorIdentities) return cycleRefusal(dirPath)
        // 거쳐 온 식별값에 없는 값만 담기므로, 나올 때 이 값들을 빼도 상위 디렉터리의 값은 남는다
        val identities = setOfNotNull(entryIdentity, listed.selfIdentity)
        ancestorIdentities.addAll(identities)
        return IoResult.Success(DirectoryVisit(listed, identities))
    }

    /**
     * 트리 수집이 [path]에서 거쳐 온 디렉터리로 돌아왔다는 실패를 만든다.
     * @param path 거쳐 온 디렉터리와 같은 디렉터리의 경로 (ftp://server/path 형식)
     * @return 순환을 알리는 실패
     */
    private fun cycleRefusal(path: String): IoResult.ItemFailed =
        IoResult.ItemFailed("폴더 순환 감지: ${extractFtpPath(path)} 은(는) 상위 폴더와 같은 폴더입니다")

    /**
     * 트리 수집이 하위 디렉터리 [path]로 내려가면 안 되는 이유를 실패로 만든다.
     * 수집이 끝나는지를 목록의 정확성에 맡기지 않는다 — 목록이 상위 디렉터리를 하위로 보여주면 경로만 길어지며 끝없이 내려간다.
     * 서버가 식별값을 알려주면(MLSD `unique`) 거쳐 온 디렉터리와 같은 디렉터리인지로 순환을 판별한다.
     * 식별값이 없으면(LIST, `unique`를 보내지 않는 MLSD) 순환과 깊은 트리를 구분할 수 없으므로, 경로가 [PATH_MAX_BYTES]에 이르면 멈춘다.
     * 이 한도는 식별값과 무관하게 수집이 끝나도록 둔 상한이라 식별값이 있어도 적용한다(서버마다 식별값을 만드는 방식을 검증할 수 없다).
     * 값은 Linux(Android·대부분의 FTP 서버)가 한 경로로 다룰 수 있는 길이이므로, 실제 트리를 자르는 일은 사실상 없다.
     * @param path 내려갈 하위 디렉터리 경로 (ftp://server/path 형식)
     * @param identity 서버가 알려준 [path]의 식별값. 없으면 null
     * @param ancestorIdentities [path]의 상위 디렉터리들의 식별값
     * @return 내려가면 안 되면 그 이유를 담은 실패, 내려가도 되면 null
     */
    private fun descentRefusal(path: String, identity: String?, ancestorIdentities: Set<String>): IoResult.ItemFailed? = when {
        identity != null && identity in ancestorIdentities -> cycleRefusal(path)
        exceedsPathMax(path) -> IoResult.ItemFailed(
            "경로가 너무 깁니다(${PATH_MAX_BYTES}바이트 이상): ${extractFtpPath(path)}" + if (identity == null) UNIDENTIFIABLE_NOTE else ""
        )
        else -> null
    }

    /**
     * [path]의 FTP 경로가 경로 한도([PATH_MAX_BYTES])에 이르는지 판별한다. 한도는 Android 파일 이름 인코딩(UTF-8) 기준이다.
     * @param path 판별할 경로 (ftp://server/path 형식)
     * @return 한도에 이르면 true
     */
    private fun exceedsPathMax(path: String): Boolean = extractFtpPath(path).toByteArray(Charsets.UTF_8).size >= PATH_MAX_BYTES

    /**
     * 원격 경로의 파일 목록을 조회한다. 화면 목록([listFiles])과 트리 수집([collectRemoteTree])이 같은 조회를 써서 둘이 보는 항목과 응답 해석이 같다.
     * 서버가 FEAT에 MLST를 알리면(RFC 3659 §7.8: MLSD 지원 포함) MLSD로, 아니면 LIST로 조회한다.
     * LIST·MLSD 모두 거부 응답은 실패다([FtpReplyRules.isListingCompleted]). 2xx로 끝난 빈 목록은 빈 폴더로 본다 — 열지 못한 폴더를 2xx로 답하는 서버는 구분할 수 없다(#266).
     * @param path ftp://server/path 형식의 절대 경로
     * @param withIdentities 서버가 알려준 항목 식별값(MLSD `unique`)도 읽으면 true — 트리 수집이 순환 판별에 쓴다
     * @return 원격 경로의 파일 목록과 서버가 알려준 항목 식별값
     */
    private suspend fun list(path: String, withIdentities: Boolean): IoResult<IdentifiedListing> {
        val ftpPath = extractFtpPath(path)
        return session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (FtpListingFormat.usesMlsd(client)) listWithMlsd(client, ftpPath, path, withIdentities)
                else listWithList(client, ftpPath, path)
            }
        }
    }

    /**
     * LIST로 목록을 조회한다. FTPClient.listFiles는 데이터 연결을 열지 못해도 빈 배열을 돌려주므로 응답으로 결과를 판별한다.
     * 목록 해석기는 연결할 때 [FtpListingFormat.isReadable]로 확인했으므로, 이 연결에서는 해석기를 고르지 못해 실패하지 않는다.
     * 목록은 서버가 드러내는 항목뿐이다 — 서버가 숨긴 항목(유닉스 계열의 `.` 항목 등)은 지원 범위 밖이라 `-a` 같은 비표준 옵션을 붙이지 않는다.
     * 디렉터리로 이동한 뒤 인자 없는 `LIST`로 조회한다(RFC 959: 인자가 없으면 현재 디렉터리, curl과 같은 방식) — LIST 인자를 옵션으로도 읽는
     * 서버(ProFTPD 등)가 있어 경로 인자의 해석이 서버마다 갈릴 수 있다. commons-net의 숨김 항목 옵션(`listHiddenFiles`)은 켜지 않으므로 `listFiles()`는 인자 없이 나간다.
     * 다른 명령은 모두 절대 경로를 쓰지만, 세션이 조회한 폴더 안에 남으므로 그 폴더나 상위 폴더를 지우거나 옮기는 명령은 먼저 밖으로 나간다([leavingTarget]).
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 디렉터리의 항목 목록(LIST 목록에는 항목을 식별할 값이 없어 식별값은 없다), 또는 서버가 거부한 실패
     */
    private fun listWithList(client: FTPClient, ftpPath: String, path: String): IoResult<IdentifiedListing> {
        if (!changeDirectory(client, ftpPath)) return FtpReplyRules.refusal(FtpCommand.CWD, client)
        val files = client.listFiles()
        return listingOutcome(FtpCommand.LIST, client, path) {
            val entries = files?.filter { it.name != "." && it.name != ".." }.orEmpty()
            ReceivedListing(entries.map { ReceivedEntry(it, identity = null) }, selfIdentity = null)
        }
    }

    /**
     * MLSD로 목록을 조회한다. mlistDir도 데이터 연결을 열지 못하면 빈 배열을 돌려주므로 응답으로 결과를 판별한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param withIdentities 서버가 알려준 항목 식별값(MLSD `unique`)도 읽으면 true — 트리 수집이 순환 판별에 쓴다
     * @return 디렉터리의 항목 목록 (자신·상위 디렉터리 항목 제외)과 서버가 알려준 식별값([receivedMlsdListing]), 또는 서버가 거부한 실패
     */
    private fun listWithMlsd(client: FTPClient, ftpPath: String, path: String, withIdentities: Boolean): IoResult<IdentifiedListing> {
        val files = client.mlistDir(ftpPath)
        return listingOutcome(FtpCommand.MLSD, client, path) {
            receivedMlsdListing(files.orEmpty(), withIdentities)
        }
    }

    /**
     * 목록 조회의 마지막 응답을 [FtpReplyRules.isListingCompleted]로 해석해 결과로 만든다. 거부 응답은 모두 이 조회의 실패다.
     * @param command 보낸 목록 조회 명령
     * @param client 응답을 받은 FTPClient
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param received 조회를 마쳤을 때 받은 항목과 식별값
     * @return 조회 결과
     */
    private fun listingOutcome(
        command: FtpCommand,
        client: FTPClient,
        path: String,
        received: () -> ReceivedListing
    ): IoResult<IdentifiedListing> =
        if (FtpReplyRules.isListingCompleted(client.replyCode)) IoResult.Success(identifiedListing(received(), path))
        else FtpReplyRules.refusal(command, client)

    /**
     * 끝까지 받은 목록의 항목을 domain 항목으로 바꾸고, 서버가 알려준 식별값을 항목 경로에 묶는다.
     *
     * 알려진 문제(방치하기로 결정): 목록은 서버가 드러내는 항목뿐이라(RFC 3659 §7.1, 유닉스 계열 LIST의 `.` 항목·ProFTPD `HideFiles` 등)
     * 서버가 숨긴 항목이 빠질 수 있지만 폴더의 목록으로 보고한다. 그래서 업로드·이름 변경의 충돌 검사가 숨긴 같은 이름 항목을 보지 못해
     * STOR·RNTO가 묻지 않고 덮어쓸 수 있다. 표준 명령으로는 항목이 없음을 증명할 수 없다(MLST·SIZE의 550은 없음과 거부를 구분하지 않는다).
     * @param received 받은 항목과 식별값
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 받은 목록과 식별값
     */
    private fun identifiedListing(received: ReceivedListing, path: String): IdentifiedListing {
        val converted = received.entries.map { it to toFileEntry(it.file, path) }
        val identities = converted.mapNotNull { (item, entry) -> item.identity?.let { entry.path to it } }.toMap()
        return IdentifiedListing(converted.map { it.second }, identities, received.selfIdentity)
    }

    /**
     * MLSD로 받은 항목의 사실을 한 번씩 읽어, 나열한 디렉터리 자신·상위 항목(type=cdir/pdir)을 걸러낸다.
     * 걸러내지 않으면 트리 수집이 자기 자신을 하위 디렉터리로 보고 내려간다. 자신 항목의 식별값은 그 디렉터리의 식별값이다.
     * @param files MLSD로 받은 항목
     * @param withIdentities 식별값을 붙일지 여부. 식별값은 트리 수집만 쓴다
     * @return 디렉터리 안의 항목과 식별값
     */
    private fun receivedMlsdListing(files: Array<out FTPFile>, withIdentities: Boolean): ReceivedListing {
        var selfIdentity: String? = null
        val entries = files.mapNotNull { file ->
            val facts = FtpListingFormat.mlsdFacts(file)
            val identity = if (withIdentities) FtpListingFormat.mlsdIdentity(facts) else null
            when (FtpListingFormat.mlsdSelfOrParent(facts)) {
                MlsdSelfOrParent.SELF -> null.also { if (selfIdentity == null) selfIdentity = identity }
                MlsdSelfOrParent.PARENT -> null
                null -> ReceivedEntry(file, identity)
            }
        }
        return ReceivedListing(entries, selfIdentity)
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
     * @param leave 명령 전에 같은 잠금 안에서 대상 밖으로 나가는 디렉터리 이동([leavingTarget]). null이면 이동하지 않는다
     * @param block 실행할 FTP 명령. 성공하면 true를 반환한다
     * @return 명령 결과
     */
    private suspend fun runFtpCommand(
        command: FtpCommand,
        leave: ((FTPClient) -> Unit)? = null,
        block: (FTPClient) -> Boolean
    ): IoResult<Unit> =
        session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                leave?.invoke(client)
                if (block(client)) IoResult.Done else FtpReplyRules.refusal(command, client)
            }
        }

    /**
     * 세션의 현재 디렉터리가 [targetFtpPath]이거나 그 안이면 대상 밖으로 옮기는 디렉터리 이동을 만든다.
     * LIST 서버의 목록 조회는 세션을 조회한 폴더 안에 남기므로, 그 폴더를 지우거나 옮기는 명령은 대상 밖에서 보낸다 —
     * 세션이 들어가 있는 디렉터리의 삭제를 거부하는 서버가 있다(Apache FTP Server, Windows 서버). 명령과 같은 잠금 안에서 실행해야 그 사이 다른 조회가 다시 들어가지 않는다.
     * 나가기는 그런 서버를 위한 사전 조치이므로, 어디로도 나가지 못해도 명령은 보내고 서버의 응답을 결과로 삼는다 —
     * 세션이 들어가 있는 디렉터리도 지울 수 있는 서버(리눅스 계열)에서 명령을 막지 않는다.
     * @param targetFtpPath 지우거나 옮길 항목의 FTP 경로
     * @param outside 먼저 옮겨 갈 디렉터리의 FTP 경로. [targetFtpPath] 밖이어야 한다
     * @return 잠금을 확보한 FTPClient로 실행할 디렉터리 이동
     */
    private fun leavingTarget(targetFtpPath: String, outside: String): (FTPClient) -> Unit = { client ->
        val current = session.workingDirectory
        if (current != null && isSameOrInside(current, targetFtpPath)) {
            exitCandidates(targetFtpPath, outside).firstOrNull { changeDirectory(client, it) }
        }
    }

    /**
     * [targetFtpPath] 밖으로 나갈 때 차례로 시도할 디렉터리. 대상의 상위 디렉터리를 먼저, 거부되면 로그인 홈([FtpSession.homeDirectory])을 시도한다.
     * 가상 경로 권한을 쓰는 서버는 상위 디렉터리로의 이동을 거부할 수 있지만, 홈은 로그인한 사용자가 들어가 있던 곳이다. 홈이 대상 안이면 시도하지 않는다.
     * @param targetFtpPath 지우거나 옮길 항목의 FTP 경로
     * @param outside 먼저 옮겨 갈 디렉터리의 FTP 경로
     * @return 시도할 디렉터리의 FTP 경로 목록
     */
    private fun exitCandidates(targetFtpPath: String, outside: String): List<String> {
        val home = session.homeDirectory?.takeUnless { isSameOrInside(it, targetFtpPath) }
        return listOfNotNull(outside, home).distinct()
    }

    /**
     * [client]의 현재 디렉터리를 [ftpPath]로 옮기고, 옮겼으면 세션에 기록한다([FtpSession.workingDirectory]).
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 옮겨 갈 디렉터리의 FTP 경로
     * @return 옮겼으면 true, 서버가 거부하면 false
     */
    private fun changeDirectory(client: FTPClient, ftpPath: String): Boolean =
        client.changeWorkingDirectory(ftpPath).also { changed -> if (changed) session.workingDirectory = ftpPath }

    /**
     * [path]가 [directoryPath] 자신이거나 그 아래인지 판별한다.
     * @param path 판별할 FTP 경로
     * @param directoryPath 기준 디렉터리의 FTP 경로
     * @return 같거나 그 아래면 true
     */
    private fun isSameOrInside(path: String, directoryPath: String): Boolean =
        path.trimEnd('/') == directoryPath.trimEnd('/') || isRemoteAncestorOf(directoryPath, path)

    companion object {
        /** 트리 수집이 내려갈 수 있는 경로 길이의 한도(바이트, 끝의 NUL 포함). Linux(Android)의 PATH_MAX다. */
        private const val PATH_MAX_BYTES = 4096
        /** 식별값 없이 경로 한도에서 멈췄을 때 덧붙이는 설명([descentRefusal]). */
        private const val UNIDENTIFIABLE_NOTE = " — 이 서버의 목록으로는 폴더가 순환하는지 판별할 수 없습니다"
    }
}
