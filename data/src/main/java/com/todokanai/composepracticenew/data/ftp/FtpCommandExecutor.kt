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
import org.apache.commons.net.ftp.FTPFileEntryParser
import org.apache.commons.net.ftp.FTPListParseEngine
import org.apache.commons.net.ftp.parser.MLSxEntryParser
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 목록 조회 결과와, 서버가 알려준 식별값(MLSD `unique`). 트리 수집이 순환을 판별하는 데 쓴다.
 * @param entries 조회한 항목
 * @param identities 항목 경로 → 식별값. 서버가 식별값을 알려준 항목만 담는다
 */
private data class IdentifiedListing(val entries: List<FileEntry>, val identities: Map<String, String>)

/**
 * 서버에게 받은 목록 항목 하나와 그 식별값.
 * @param file 받은 항목
 * @param identity 서버가 알려준 식별값. 없으면 null
 */
private data class ReceivedEntry(val file: FTPFile, val identity: String?)

/**
 * 트리 수집이 들어가 있는 디렉터리 하나.
 * @param listed 그 디렉터리의 목록
 * @param identity 상위 목록의 항목이 알려준 그 디렉터리의 식별값. 모르거나 시작 디렉터리면 null
 */
private class DirectoryVisit(val listed: IdentifiedListing, val identity: String?) {
    /** 아직 수집하지 않은 항목. */
    val remaining: Iterator<FileEntry> = listed.entries.iterator()
}

/**
 * 트리 수집 하나가 받은 목록의 메모리를 추정해 더한다. 받은 항목은 수집이 끝날 때까지 메모리에 남으므로, 받은 만큼이 수집이 쓰는 메모리다.
 *
 * 세지 않는 메모리가 있다 — 들어가 있는 디렉터리마다 그 목록의 식별값([IdentifiedListing.identities])이 남지만 더하지 않는다.
 * 주류 서버의 `unique`는 장치 번호·inode의 16진수(ProFTPD `%lXU%lX`, Pure-FTPd `%xg%llx`, 약 33자 이하)라 정상 트리에서는
 * 실제 메모리가 상한을 조금(경로 위 폴더에 항목이 아주 많으면 약 10MiB) 넘는 정도다. 그러나 RFC 3659의 `unique` 문법
 * (`token = 1*RCHAR`)에는 길이 상한이 없어, 수 KB 이상의 `unique`를 보내는 서버에서는 깊이마다 쌓여 상한에 이르기 전에 메모리가 바닥날 수 있다.
 * @param limitBytes 수집이 쓸 수 있는 메모리의 추정 상한(바이트)
 */
private class TreeMemoryBudget(private val limitBytes: Long) {
    /** 지금까지 받은 항목의 추정 메모리(바이트). */
    private var usedBytes = 0L

    /** 다음 목록을 받는 동안 쓸 수 있는 추정 메모리(바이트). [admit]이 상한을 넘기면 수집이 끝나므로 음수가 되지 않는다. */
    val remainingBytes: Long get() = limitBytes - usedBytes

    /**
     * 받은 목록의 추정 메모리를 더한다.
     * @param entries 받은 목록의 항목
     * @return 더한 뒤에도 상한 안이면 true
     */
    fun admit(entries: List<FileEntry>): Boolean {
        usedBytes += entries.sumOf(::estimatedBytes)
        return usedBytes <= limitBytes
    }

    /**
     * 항목 하나가 차지하는 메모리의 상한 추정치. 고정 부분([ENTRY_OVERHEAD_BYTES])에 경로·이름을 문자당 2바이트(UTF-16)로 더한다 —
     * ASCII만인 문자열은 실제로 그 절반이므로, 어느 문자로 된 경로든 항목 자체의 실제 메모리가 추정치를 넘지 않는다(식별값은 세지 않는다 — [TreeMemoryBudget]).
     * @param entry 받은 항목
     * @return 추정 메모리(바이트)
     */
    private fun estimatedBytes(entry: FileEntry): Long = ENTRY_OVERHEAD_BYTES + 2L * (entry.path.length + entry.name.length)

    private companion object {
        /**
         * 항목 하나에서 경로·이름의 문자 데이터를 뺀 메모리(바이트): 항목·문자열·크기 객체의 헤더와 필드, 목록의 참조.
         * JVM에서 경로 79자인 ASCII 항목 10만 개가 항목당 약 269바이트였다(문자 데이터 약 100바이트를 빼면 약 165바이트).
         */
        const val ENTRY_OVERHEAD_BYTES = 170L
    }
}

/**
 * 목록 하나를 받는 동안 쓸 수 있는 메모리의 추정 상한과, 넘었을 때 그 조회의 결과로 돌려줄 실패. 트리 수집만 쓴다.
 * @param peakBytes 목록을 받고 해석하는 동안의 추정 메모리 상한(바이트, [BoundedListingStream])
 * @param exceeded 상한을 넘었을 때 돌려줄 실패
 */
private class ListingLimit(val peakBytes: Long, val exceeded: IoResult.ItemFailed)

/** 목록을 받는 동안의 추정 메모리가 상한을 넘어 받기를 멈췄다([BoundedListingStream]). 데이터 연결의 실패로 전파되어 연결을 중단으로 닫게 한다. */
private class ListingTooLargeException : IOException("목록이 메모리 상한을 넘었습니다")

/**
 * 받은 목록 데이터의 양으로 그 목록을 받고 해석하는 동안의 메모리를 추정해, 상한을 넘으면 받기를 멈추는 스트림.
 * 목록 해석기는 받은 줄을 모두 들고 있다가 항목으로 바꾸므로, 끝까지 받은 뒤에 세면 한 폴더의 항목이 아주 많거나
 * 서버가 목록을 끝없이 보낼 때 세기 전에 메모리가 바닥난다. 데이터가 조금씩이라도 계속 오면 읽기 상한에도 걸리지 않는다.
 *
 * 변환한 항목의 경로는 조회한 디렉터리의 경로를 앞부분으로 가지지만 받은 데이터에는 그 앞부분이 없으므로, 줄마다 앞부분의 길이를 문자당
 * 2바이트로 더한다 — 세지 않으면 깊은 폴더(앞부분 수천 자)의 큰 목록은 받는 동안의 추정치보다 수십 배의 메모리를 받은 뒤의 추정([TreeMemoryBudget])
 * 전에 쓴다. 그래서 받는 동안의 추정치가 같은 목록의 받은 뒤 추정치보다 항상 크다.
 *
 * 줄 끝은 해석기(`BufferedReader.readLine`)와 같은 규칙으로 센다 — CR·LF·CRLF가 각각 줄 끝 하나다. 해석기가 들고 있는 줄 수와 달리 세면
 * 줄마다의 메모리([lineOverheadBytes])가 빠진다 — LF만 세면 줄을 CR로만 끝내는 목록(LIST는 binary(`TYPE I`)로 받아 서버 시스템의 줄 끝이
 * 오므로 클래식 Mac OS 서버)에서 실제 메모리가 추정치의 수십 배(깊은 폴더)가 되고, CR·LF를 따로 세면 CRLF 목록(MLSD는 줄 끝이 CRLF여야 한다,
 * RFC 3659 `data-response = *( entry CRLF )`)의 추정치가 두 배 가까이 부풀어 상한 안의 트리를 실패시킨다.
 * @param stream 목록 데이터 스트림
 * @param peakBytes 추정 메모리 상한(바이트)
 * @param entryPathPrefixLength 변환한 항목 경로의 앞부분(조회한 디렉터리의 경로)의 문자 수
 */
private class BoundedListingStream(
    stream: InputStream,
    private val peakBytes: Long,
    entryPathPrefixLength: Int
) : FilterInputStream(stream) {
    /** 줄 하나를 받고 해석·변환하는 동안 받은 데이터와 별도로 드는 메모리(바이트): 고정 부분과 항목 경로의 앞부분. */
    private val lineOverheadBytes = LINE_OVERHEAD_BYTES + 2L * entryPathPrefixLength
    /** 지금까지 받은 바이트 수. */
    private var receivedBytes = 0L
    /** 지금까지 받은 줄 끝 수 — 해석기가 들고 있는 줄 수다. */
    private var receivedLines = 0L
    /** 직전에 받은 바이트가 CR인지. CRLF를 줄 끝 하나로 세려고 읽기 사이에도 남긴다. */
    private var afterCarriageReturn = false

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) count(1, if (endsLine(b)) 1 else 0)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) count(n.toLong(), (off until off + n).count { endsLine(b[it].toInt()) }.toLong())
        return n
    }

    /**
     * 받은 바이트 하나가 줄을 끝내는지 해석기와 같은 규칙으로 판별한다 — CR은 줄 끝이고, LF는 CR 바로 뒤(CRLF)가 아닐 때만 줄 끝이다.
     * 받은 순서대로 불러야 한다.
     * @param byte 받은 바이트
     * @return 이 바이트로 줄 하나가 끝났으면 true
     */
    private fun endsLine(byte: Int): Boolean {
        val ends = byte == CARRIAGE_RETURN || (byte == LINE_FEED && !afterCarriageReturn)
        afterCarriageReturn = byte == CARRIAGE_RETURN
        return ends
    }

    /**
     * 받은 양을 더하고, 추정 메모리가 상한을 넘었으면 받기를 멈춘다.
     * @param bytes 새로 받은 바이트 수
     * @param lines 새로 받은 줄 끝 수
     * @throws ListingTooLargeException 추정 메모리가 상한을 넘은 경우
     */
    private fun count(bytes: Long, lines: Long) {
        receivedBytes += bytes
        receivedLines += lines
        if (lineOverheadBytes * receivedLines + BYTE_FACTOR * receivedBytes > peakBytes) throw ListingTooLargeException()
    }

    private companion object {
        /** 줄바꿈(LF) 바이트. */
        const val LINE_FEED = '\n'.code
        /** 캐리지 리턴(CR) 바이트. */
        const val CARRIAGE_RETURN = '\r'.code
        /**
         * 줄 하나를 받고 해석하는 동안 문자 데이터와 별도로 드는 메모리(바이트): 줄 문자열·해석한 항목·변환한 항목의 객체와 목록의 참조.
         * JVM에서 줄 10만 개를 받아 해석·변환한 뒤 모두 들고 있으니 줄마다 236~372바이트였다(LIST·MLSD, 줄 17~88바이트).
         * 이 값과 [BYTE_FACTOR]로 낸 추정치(250 + 4 × 줄 바이트)는 네 경우 모두 측정값의 1.4배 이상이다.
         * 항목 경로의 앞부분은 폴더의 깊이에 따라 달라 이 값에 넣지 않고 따로 더한다([lineOverheadBytes]).
         */
        const val LINE_OVERHEAD_BYTES = 250L
        /**
         * 받은 바이트 하나당 드는 메모리(바이트). 줄 문자열(ASCII는 바이트당 1, 그 밖은 문자당 2)과 변환한 항목의 이름·경로의 이름 부분을 함께 들고,
         * 줄바꿈 없이 긴 줄은 읽는 동안 버퍼가 두 배씩 자라므로 넉넉히 잡는다.
         */
        const val BYTE_FACTOR = 4L
    }
}

/**
 * FTP 파일 명령(목록 조회·이름변경·삭제·생성)을 FtpSession을 통해 실행한다.
 * 모든 명령은 실패를 결과로 알리며, 연결이 없거나 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * 서버 응답의 해석은 [FtpReplyRules]에 맡긴다.
 * @param session 명령을 실행할 FTPClient와 잠금을 소유한 FTP 세션
 * @param treeMemoryLimitBytes 트리 수집 하나가 받은 목록에 쓸 수 있는 메모리의 추정 상한(바이트, [collectRemoteTree]). 0보다 커야 한다
 */
@Singleton
class FtpCommandExecutor internal constructor(
    private val session: FtpSession,
    private val treeMemoryLimitBytes: Long
) {

    /** 트리 수집 메모리 상한을 [TREE_MEMORY_LIMIT_BYTES]로 둔 명령 실행기를 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor(session: FtpSession) : this(session, TREE_MEMORY_LIMIT_BYTES)

    init {
        // 0 이하는 상한이 아니다 — 어떤 목록도 받을 수 없어 빈 시작 폴더 말고는 모두 실패한다
        require(treeMemoryLimitBytes > 0) { "treeMemoryLimitBytes must be positive: $treeMemoryLimitBytes" }
    }

    /** 목록 조회 명령(LIST·MLSD)을 전송과 같은 규칙으로 실행한다. */
    private val dataCommands = DataCommandRunner(session)

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
     * 목록이 상위 디렉터리를 하위로 보여줘도(디렉터리로 보고된 링크, 서버 오류) 수집은 끝난다 — 서버가 무엇을 보여주든 받은 목록의 추정 메모리가
     * [treeMemoryLimitBytes]를 넘으면 멈추므로([TreeMemoryBudget]), 순환과 구분할 수 없는 큰 트리(아래 지원 범위)와 순환 모두 실패로 끝난다.
     * 방문한 디렉터리도 모두 받은 항목이므로 이 상한이 메모리와 함께 방문 수(목록 조회 횟수)도 막는다.
     * 그 위에서 서버가 식별값(MLSD `unique`)을 알려주면 순환을 더 일찍, 순환이라는 이유로 알린다([descentRefusal]).
     * 지원 범위: 받은 목록의 추정 메모리가 상한 안인 트리. 넘는 트리는 일부만 처리하지 않고 실패로 알린다.
     * 순환 감지는 같은 대상에 어느 경로로든 같은 식별값을 주는 서버(RFC 3659 §7.5.2)만 지원한다 — 경로마다 다른 값을 주는 서버의 순환은
     * 순환이 아닌 큰 트리와 구분할 수 없어 상한·경로 한도의 실패로 끝난다.
     * 깊이는 경로 한도까지 수천 단계가 될 수 있어, 재귀 대신 들어가 있는 디렉터리를 쌓아 두고 거쳐 온 식별값을 한 집합에서 넣고 뺀다 —
     * 스레드 스택 깊이에 기대지 않는다.
     * 알려진 문제(방치하기로 결정): 각 목록은 서버가 드러내는 항목뿐이라([identifiedListing]) 서버가 숨긴 항목은 트리에서 빠지지만, 수집은 성공으로 끝난다.
     * 그래서 폴더 다운로드는 숨긴 항목 없이 완료로 보고되고, 재귀 삭제는 보이는 항목을 지운 뒤 RMD에서 실패한다([removeDirectoryRecursive]).
     * @param remoteDirPath 수집할 원격 디렉터리 경로 (ftp://server/path 형식)
     * @return 하위 항목 전체 목록, 또는 조회 실패나 메모리 상한·순환·경로 한도에 의한 실패
     */
    internal suspend fun collectRemoteTree(remoteDirPath: String): IoResult<List<FileEntry>> {
        val result = mutableListOf<FileEntry>()
        val budget = TreeMemoryBudget(treeMemoryLimitBytes)
        // 거쳐 온 디렉터리의 식별값 — 이미 있는 값이면 내려가지 않으므로 같은 값이 두 번 들어가지 않아, 그 디렉터리를 나올 때 빼도 된다
        val ancestorIdentities = HashSet<String>()
        // 들어가 있는 하위 디렉터리 중 식별값을 모르는 것의 수(시작 디렉터리 제외) — 순환은 지금 경로 위에서만 일어나므로,
        // 0보다 크면 지금 경로가 순환하는지 식별값으로 판별할 수 없다. 끝낸 형제 디렉터리는 순환이 아니었으므로 세지 않는다
        var unidentifiedOnPath = 0
        val pending = ArrayDeque<DirectoryVisit>()
        pending.addLast(enterDirectory(remoteDirPath, identity = null, budget, undecidable = false).valueOr { return it })
        while (pending.isNotEmpty()) {
            val visit = pending.last()
            if (!visit.remaining.hasNext()) {
                pending.removeLast()
                // 스택이 비었으면 방금 나온 것은 시작 디렉터리다
                if (visit.identity != null) ancestorIdentities.remove(visit.identity) else if (pending.isNotEmpty()) unidentifiedOnPath--
                continue
            }
            val entry = visit.remaining.next()
            result.add(entry)
            if (!entry.isDirectory) continue
            val identity = visit.listed.identities[entry.path]
            if (identity == null) unidentifiedOnPath++
            descentRefusal(entry.path, identity, ancestorIdentities, undecidable = unidentifiedOnPath > 0)?.let { return it }
            pending.addLast(enterDirectory(entry.path, identity, budget, undecidable = unidentifiedOnPath > 0).valueOr { return it })
            identity?.let(ancestorIdentities::add)
        }
        return IoResult.Success(result)
    }

    /**
     * 트리 수집이 [dirPath]로 들어간다. 목록을 조회하고, 받은 목록을 수집의 메모리 상한에 더한다.
     * 목록을 받는 동안에도 남은 상한을 넘으면 받기를 멈춘다([BoundedListingStream]) — 끝까지 받은 뒤에만 세면 목록 하나가 상한을 넘겨도 막지 못한다.
     * @param dirPath 들어갈 원격 디렉터리 경로 (ftp://server/path 형식)
     * @param identity 상위 목록의 항목이 알려준 이 디렉터리의 식별값. 모르거나 시작 디렉터리면 null
     * @param budget 이 수집의 메모리 상한
     * @param undecidable 이 디렉터리까지의 경로가 순환하는지 식별값으로 판별할 수 없으면 true — 상한에 이른 실패에 그 설명을 붙인다
     * @return 들어간 디렉터리. 조회에 실패했거나 받은 목록이 상한을 넘기면 그 실패
     */
    private suspend fun enterDirectory(
        dirPath: String,
        identity: String?,
        budget: TreeMemoryBudget,
        undecidable: Boolean
    ): IoResult<DirectoryVisit> {
        val tooMany = IoResult.ItemFailed("$TOO_MANY_ENTRIES_MESSAGE: ${extractFtpPath(dirPath)}" + if (undecidable) UNIDENTIFIABLE_NOTE else "")
        val listed = list(dirPath, withIdentities = true, ListingLimit(budget.remainingBytes, tooMany)).valueOr { return it }
        if (!budget.admit(listed.entries)) return tooMany
        return IoResult.Success(DirectoryVisit(listed, identity))
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
     * 서버가 식별값을 알려주면(MLSD `unique`, 서버가 이미 목록에 담는 경우) 거쳐 온 디렉터리와 같은 디렉터리인지로 순환을 판별한다 —
     * 수집이 끝나는 것은 메모리 상한([TreeMemoryBudget])이 보장하고, 이 판별은 순환을 상한보다 일찍, 순환이라는 이유로 알린다.
     * 경로가 [PATH_MAX_BYTES]에 이르면 식별값과 무관하게 멈춘다. 값은 Linux(Android·대부분의 FTP 서버)가 한 경로로 다룰 수 있는 길이라
     * 그보다 깊은 항목은 내려받아도 로컬에 만들 수 없고, 실제 트리를 자르는 일은 사실상 없다.
     * @param path 내려갈 하위 디렉터리 경로 (ftp://server/path 형식)
     * @param identity 서버가 알려준 [path]의 식별값. 없으면 null
     * @param ancestorIdentities [path]의 상위 디렉터리들의 식별값
     * @param undecidable [path]까지의 경로가 순환하는지 식별값으로 판별할 수 없으면 true — 경로 한도 실패에 그 설명을 붙인다
     * @return 내려가면 안 되면 그 이유를 담은 실패, 내려가도 되면 null
     */
    private fun descentRefusal(path: String, identity: String?, ancestorIdentities: Set<String>, undecidable: Boolean): IoResult.ItemFailed? = when {
        identity != null && identity in ancestorIdentities -> cycleRefusal(path)
        exceedsPathMax(path) -> IoResult.ItemFailed(
            "경로가 너무 깁니다(${PATH_MAX_BYTES}바이트 이상): ${extractFtpPath(path)}" + if (undecidable) UNIDENTIFIABLE_NOTE else ""
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
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다(화면 목록)
     * @return 원격 경로의 파일 목록과 서버가 알려준 항목 식별값
     */
    private suspend fun list(path: String, withIdentities: Boolean, limit: ListingLimit? = null): IoResult<IdentifiedListing> {
        val ftpPath = extractFtpPath(path)
        return session.withConnectedClient { client ->
            withContext(Dispatchers.IO) {
                if (FtpListingFormat.usesMlsd(client)) listWithMlsd(client, ftpPath, path, withIdentities, limit)
                else listWithList(client, ftpPath, path, limit)
            }
        }
    }

    /**
     * LIST로 목록을 조회한다. 목록 해석기는 연결할 때 [FtpListingFormat.isReadable]로 확인했으므로, 이 연결에서는 해석기를 고르지 못해 실패하지 않는다.
     * 목록은 서버가 드러내는 항목뿐이다 — 서버가 숨긴 항목(유닉스 계열의 `.` 항목 등)은 지원 범위 밖이라 `-a` 같은 비표준 옵션을 붙이지 않는다.
     * 디렉터리로 이동한 뒤 인자 없는 `LIST`로 조회한다(RFC 959: 인자가 없으면 현재 디렉터리, curl과 같은 방식) — LIST 인자를 옵션으로도 읽는
     * 서버(ProFTPD 등)가 있어 경로 인자의 해석이 서버마다 갈릴 수 있다.
     * 다른 명령은 모두 절대 경로를 쓰지만, 세션이 조회한 폴더 안에 남으므로 그 폴더나 상위 폴더를 지우거나 옮기는 명령은 먼저 밖으로 나간다([leavingTarget]).
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다
     * @return 디렉터리의 항목 목록(LIST 목록에는 항목을 식별할 값이 없어 식별값은 없다), 또는 조회 실패
     */
    private suspend fun listWithList(client: ListingStreamFtpClient, ftpPath: String, path: String, limit: ListingLimit?): IoResult<IdentifiedListing> {
        if (!changeDirectory(client, ftpPath)) return FtpReplyRules.refusal(FtpCommand.CWD, client.replyString)
        return receiveListing(FtpCommand.LIST, client, FtpListingFormat.listParser(client), path, limit, open = { client.openListStream() }) { files ->
            files.filter { it.name != "." && it.name != ".." }.map { ReceivedEntry(it, identity = null) }
        }
    }

    /**
     * MLSD로 목록을 조회한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param withIdentities 서버가 알려준 항목 식별값(MLSD `unique`)도 읽으면 true — 트리 수집이 순환 판별에 쓴다
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다
     * @return 디렉터리의 항목 목록 (자신·상위 디렉터리 항목 제외)과 서버가 알려준 식별값([receivedMlsdEntries]), 또는 조회 실패
     */
    private suspend fun listWithMlsd(
        client: ListingStreamFtpClient,
        ftpPath: String,
        path: String,
        withIdentities: Boolean,
        limit: ListingLimit?
    ): IoResult<IdentifiedListing> =
        receiveListing(FtpCommand.MLSD, client, MLSxEntryParser.getInstance(), path, limit, open = { client.openMlsdStream(ftpPath) }) { files ->
            receivedMlsdEntries(files, withIdentities)
        }

    /**
     * 목록 조회 명령을 전송과 같은 규칙([DataCommandRunner])으로 실행하고, 받은 목록을 [parser]로 해석해 결과로 만든다.
     * 서버가 데이터 연결을 열기 전에 거부했거나 마지막 응답이 거부([FtpReplyRules.isListingCompleted])면 이 조회의 실패다.
     * 목록 데이터를 받다 실패하면(읽기 상한 초과 포함) 받은 일부를 목록으로 보지 않고 이 조회의 실패로 돌려준다 — 완료 응답까지 읽었으므로 세션은 유지된다.
     * 받는 동안의 메모리 상한([limit])을 넘은 것도 데이터를 받다 난 실패라 같은 경로로 연결을 중단으로 닫고, 결과는 [ListingLimit.exceeded]다.
     * @param command 보낼 목록 조회 명령
     * @param client 잠금을 확보한 FTPClient
     * @param parser 목록 줄 해석기
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다
     * @param open 명령을 보내 목록 데이터 스트림을 여는 동작. 서버가 거부하면 null을 돌려준다
     * @param received 끝까지 받은 항목에서 디렉터리 안의 항목과 식별값을 고르는 동작
     * @return 조회 결과
     */
    private suspend fun receiveListing(
        command: FtpCommand,
        client: FTPClient,
        parser: FTPFileEntryParser,
        path: String,
        limit: ListingLimit?,
        open: () -> InputStream?,
        received: (Array<FTPFile>) -> List<ReceivedEntry>
    ): IoResult<IdentifiedListing> {
        val engine = FTPListParseEngine(parser)
        val outcome = dataCommands.run(client, open) { stream ->
            val bounded = limit?.let { BoundedListingStream(stream, it.peakBytes, childFtpUriPrefix(path).length) } ?: stream
            engine.readServerList(keepingOpen(bounded), client.controlEncoding)
        }
        return when (outcome) {
            DataCommandOutcome.Refused -> FtpReplyRules.refusal(command, client.replyString)
            is DataCommandOutcome.DataFailed ->
                if (limit != null && outcome.cause is ListingTooLargeException) limit.exceeded
                else IoResult.ItemFailed("${command.label} 실패: ${outcome.cause.message ?: outcome.cause.javaClass.simpleName}")
            is DataCommandOutcome.Finished ->
                if (FtpReplyRules.isListingCompleted(client.replyCode)) IoResult.Success(identifiedListing(received(engine.files), path))
                else FtpReplyRules.refusal(command, client.replyString)
        }
    }

    /**
     * [stream]을 닫아도 닫히지 않는 스트림으로 감싼다. 목록 해석기(`FTPListParseEngine.readServerList`)는 읽기를 마치거나 실패하면 스트림을 닫는데,
     * 그 닫기는 정상 닫기라 데이터 연결을 어떻게 닫을지(정상·중단) 정하는 [DataCommandRunner]보다 먼저 연결을 닫아 버린다.
     * @param stream 목록 데이터 스트림
     * @return 닫기를 무시하는 스트림
     */
    private fun keepingOpen(stream: InputStream): InputStream = object : FilterInputStream(stream) {
        override fun close() {}
    }

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
    private fun identifiedListing(received: List<ReceivedEntry>, path: String): IdentifiedListing {
        val converted = received.map { it to toFileEntry(it.file, path) }
        val identities = converted.mapNotNull { (item, entry) -> item.identity?.let { entry.path to it } }.toMap()
        return IdentifiedListing(converted.map { it.second }, identities)
    }

    /**
     * MLSD로 받은 항목의 사실을 한 번씩 읽어, 나열한 디렉터리 자신·상위 항목(type=cdir/pdir)을 걸러낸다.
     * 걸러내지 않으면 트리 수집이 자기 자신을 하위 디렉터리로 보고 내려간다.
     * @param files MLSD로 받은 항목
     * @param withIdentities 식별값을 붙일지 여부. 식별값은 트리 수집만 쓴다
     * @return 디렉터리 안의 항목과 식별값
     */
    private fun receivedMlsdEntries(files: Array<out FTPFile>, withIdentities: Boolean): List<ReceivedEntry> =
        files.mapNotNull { file ->
            val facts = FtpListingFormat.mlsdFacts(file)
            if (FtpListingFormat.isMlsdSelfOrParent(facts)) null
            else ReceivedEntry(file, if (withIdentities) FtpListingFormat.mlsdIdentity(facts) else null)
        }

    /**
     * [ancestorPath]가 [path]를 하위 항목으로 품는 원격 디렉터리 경로인지 판별한다.
     * @param ancestorPath 상위 후보 디렉터리 경로
     * @param path 하위 후보 항목 경로
     * @return [path]가 [ancestorPath] 아래에 있으면 true
     */
    private fun isRemoteAncestorOf(ancestorPath: String, path: String): Boolean = path.startsWith(childFtpUriPrefix(ancestorPath))

    /**
     * FTP 목록 항목 하나를 domain 항목으로 바꾼다.
     * @param file 변환할 FTP 목록 항목
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 변환된 항목
     */
    private fun toFileEntry(file: FTPFile, path: String): FileEntry = FileEntry(
        name = file.name,
        path = childFtpUri(path, file.name),
        isDirectory = file.isDirectory,
        // 디렉터리는 자기 크기가 없고, FTPFile.size는 목록에 크기가 없으면 -1이다
        size = file.size.takeUnless { file.isDirectory || it < 0 },
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
                if (block(client)) IoResult.Done else FtpReplyRules.refusal(command, client.replyString)
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
        /**
         * 트리 수집 하나가 받은 목록에 쓸 수 있는 메모리의 추정 상한(바이트, [TreeMemoryBudget]). 받은 항목은 수집이 끝날 때까지 메모리에 남는다.
         * 흔한 앱 힙(약 256MB, 기기마다 다름)의 일부로, 경로 80자인 항목이면 약 9만 개, 경로가 [PATH_MAX_BYTES]에 이르는 항목이면 약 4천 개다.
         */
        private const val TREE_MEMORY_LIMIT_BYTES = 32L * 1024 * 1024
        /** 트리 수집이 메모리 상한([TREE_MEMORY_LIMIT_BYTES])에 이르렀을 때의 실패 메시지. 뒤에 멈춘 디렉터리 경로가 붙는다([enterDirectory]). */
        private const val TOO_MANY_ENTRIES_MESSAGE = "폴더 안의 항목이 너무 많아 처리하지 않았습니다"
        /** 식별값 없이 경로 한도나 메모리 상한에서 멈췄을 때 덧붙이는 설명([descentRefusal], [enterDirectory]). */
        private const val UNIDENTIFIABLE_NOTE = " — 이 서버의 목록으로는 폴더가 순환하는지 판별할 수 없습니다"
    }
}
