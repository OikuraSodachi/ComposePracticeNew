package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.map
import com.todokanai.composepracticenew.data.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPFileEntryParser
import org.apache.commons.net.ftp.parser.MLSxEntryParser
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 목록 조회 결과와, 서버가 알려준 식별값(MLSD `unique`). 트리 수집이 순환을 판별하는 데 쓴다.
 * @param items 조회한 항목과 해석하지 못한 줄
 * @param identities 항목 경로 → 식별값. 서버가 식별값을 알려준 항목만 담는다
 */
internal data class IdentifiedListing(val items: List<ListedItem>, val identities: Map<String, String>)

/**
 * 서버에게 받은 목록 항목 하나와 그 식별값.
 * @param file 받은 항목
 * @param identity 서버가 알려준 식별값. 없으면 null
 */
private data class ReceivedEntry(val file: FTPFile, val identity: String?)

/**
 * 목록 하나를 받는 동안 쓸 수 있는 메모리의 추정 상한과, 넘었을 때 그 조회의 결과로 돌려줄 실패. 트리 수집만 쓴다.
 * @param peakBytes 목록을 받고 해석하는 동안의 추정 메모리 상한(바이트, [BoundedListingStream])
 * @param exceeded 상한을 넘었을 때 돌려줄 실패
 */
internal class ListingLimit(val peakBytes: Long, val exceeded: IoResult.ItemFailed)

/** 목록을 받는 동안의 추정 메모리가 상한을 넘어 받기를 멈췄다([BoundedListingStream]). 데이터 연결의 실패로 전파되어 연결을 중단으로 닫게 한다. */
private class ListingTooLargeException : IOException("목록이 메모리 상한을 넘었습니다")

/**
 * 받은 목록 데이터의 양으로 그 목록을 받고 해석하는 동안의 메모리를 추정해, 상한을 넘으면 받기를 멈추는 스트림.
 * 목록 해석기는 받은 줄을 모두 들고 있다가 항목으로 바꾸므로, 끝까지 받은 뒤에 세면 한 폴더의 항목이 아주 많거나
 * 서버가 목록을 끝없이 보낼 때 세기 전에 메모리가 바닥난다. 데이터가 조금씩이라도 계속 오면 읽기 상한에도 걸리지 않는다.
 *
 * 변환한 항목의 경로는 조회한 디렉터리의 경로를 앞부분으로 가지지만 받은 데이터에는 그 앞부분이 없으므로, 줄마다 앞부분의 길이를 문자당
 * 2바이트로 더한다 — 세지 않으면 깊은 폴더(앞부분 수천 자)의 큰 목록은 받는 동안의 추정치보다 수십 배의 메모리를 받은 뒤의 추정([FtpTreeCollector]의 `TreeMemoryBudget`)
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
 * 원격 디렉터리의 목록을 FtpSession을 통해 받아 항목으로 해석한다. 화면 목록([listFiles])과 트리 수집([FtpTreeCollector])이
 * 같은 조회를 써서 둘이 보는 항목과 응답 해석이 같다.
 * 실패는 결과로 알리며, 연결이 없거나 끊기면 [IoResult.ConnectionLost]를 돌려준다. 서버 응답의 해석은 [FtpReplyRules]에 맡긴다.
 * @param session 조회할 FTPClient와 잠금을 소유한 FTP 세션
 * @param dataCommands 목록 조회 명령(LIST·MLSD)을 전송과 같은 규칙으로 실행하는 실행기
 */
class FtpListingReader internal constructor(
    private val session: FtpCommandSession,
    private val dataCommands: DataCommandRunner
) {

    /**
     * 화면에 보여줄 원격 경로의 파일 목록을 조회한다.
     * @param path ftp://server/path 형식의 절대 경로
     * @return 원격 경로의 항목과 해석하지 못한 줄, 또는 서버가 거부한 실패
     */
    internal suspend fun listFiles(path: String): IoResult<List<ListedItem>> = list(path, withIdentities = false).map { it.items }

    /**
     * 원격 경로의 파일 목록을 조회한다.
     * 서버가 FEAT에 MLST를 알리면(RFC 3659 §7.8: MLSD 지원 포함) MLSD로, 아니면 LIST로 조회한다.
     * LIST·MLSD 모두 거부 응답은 실패다([FtpReplyRules.isListingCompleted]). 2xx로 끝난 빈 목록은 빈 폴더로 본다 — 열지 못한 폴더를 2xx로 답하는 서버는 응답 코드로 구분할 수 없다(#266).
     * @param path ftp://server/path 형식의 절대 경로
     * @param withIdentities 서버가 알려준 항목 식별값(MLSD `unique`)도 읽으면 true — 트리 수집이 순환 판별에 쓴다
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다(화면 목록)
     * @return 원격 경로의 파일 목록과 서버가 알려준 항목 식별값
     */
    internal suspend fun list(path: String, withIdentities: Boolean, limit: ListingLimit? = null): IoResult<IdentifiedListing> {
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
     * 다른 명령은 모두 절대 경로를 쓰지만, 세션이 조회한 폴더 안에 남으므로 그 폴더나 상위 폴더를 지우거나 옮기는 명령은 먼저 밖으로 나간다([FtpCommandRunner.leavingTarget]).
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다
     * @return 디렉터리의 항목 목록(LIST 목록에는 항목을 식별할 값이 없어 식별값은 없다), 또는 조회 실패
     */
    private suspend fun listWithList(client: FtpCommandClient, ftpPath: String, path: String, limit: ListingLimit?): IoResult<IdentifiedListing> {
        if (!client.changeDirectory(ftpPath)) return FtpReplyRules.refusal(FtpCommand.CWD, client.getReplyString())
        return receiveListing(DataStreamRequest.List, client, FtpListingFormat.listParser(client), path, limit) { file ->
            ReceivedEntry(file, identity = null).takeIf { file.name != "." && file.name != ".." }
        }
    }

    /**
     * MLSD로 목록을 조회한다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 조회할 디렉터리의 FTP 경로
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param withIdentities 서버가 알려준 항목 식별값(MLSD `unique`)도 읽으면 true — 트리 수집이 순환 판별에 쓴다
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다
     * @return 디렉터리의 항목 목록 (자신·상위 디렉터리 항목 제외)과 서버가 알려준 식별값([receivedMlsdEntry]), 또는 조회 실패
     */
    private suspend fun listWithMlsd(
        client: FtpCommandClient,
        ftpPath: String,
        path: String,
        withIdentities: Boolean,
        limit: ListingLimit?
    ): IoResult<IdentifiedListing> =
        receiveListing(DataStreamRequest.Mlsd(ftpPath), client, MLSxEntryParser.getInstance(), path, limit) { file ->
            receivedMlsdEntry(file, withIdentities)
        }

    /**
     * 목록 조회 명령을 전송과 같은 규칙([DataCommandRunner])으로 실행하고, 받은 목록을 줄마다 [parser]로 해석해 결과로 만든다.
     * 서버가 데이터 연결을 열기 전에 거부했거나 마지막 응답이 거부([FtpReplyRules.isListingCompleted])면 이 조회의 실패다.
     * 목록 데이터를 받다 실패하면(읽기 상한 초과 포함) 받은 일부를 목록으로 보지 않고 이 조회의 실패로 돌려준다 — 완료 응답까지 읽었으므로 세션은 유지된다.
     * 받는 동안의 메모리 상한([limit])을 넘은 것도 데이터를 받다 난 실패라 같은 경로로 연결을 중단으로 닫고, 결과는 [ListingLimit.exceeded]다.
     * @param request 보낼 목록 조회 명령과 그 명령으로 목록 데이터 스트림을 여는 동작
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param parser 목록 줄 해석기
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @param limit 목록을 받는 동안의 메모리 상한. null이면 두지 않는다
     * @param received 해석한 항목이 디렉터리 안의 항목이면 그 항목과 식별값을, 아니면(자신·상위 항목) null을 돌려주는 동작
     * @return 조회 결과
     */
    private suspend fun receiveListing(
        request: DataStreamRequest<InputStream>,
        client: FtpCommandClient,
        parser: FTPFileEntryParser,
        path: String,
        limit: ListingLimit?,
        received: (FTPFile) -> ReceivedEntry?
    ): IoResult<IdentifiedListing> {
        var lines = emptyList<String>()
        val outcome = dataCommands.run(client, request) { stream ->
            val bounded = limit?.let { BoundedListingStream(stream, it.peakBytes, childFtpUriPrefix(path).length) } ?: stream
            lines = FtpListingFormat.readLines(bounded, client.getControlEncoding(), parser)
        }
        return when (outcome) {
            is DataCommandOutcome.Refused -> FtpReplyRules.dataCommandRefusal(outcome.step, client.getReplyString())
            is DataCommandOutcome.DataFailed ->
                if (limit != null && outcome.cause is ListingTooLargeException) limit.exceeded
                else IoResult.ItemFailed(FailureReason.RemoteStepFailed(request.command.step, FailureReason.IoFailed(outcome.cause.message ?: outcome.cause.javaClass.simpleName)))
            is DataCommandOutcome.NotConnected -> IoResult.ItemFailed(FailureReason.RemoteStepFailed(request.command.step, dataConnectionFailure(outcome.cause)))
            is DataCommandOutcome.Finished ->
                if (FtpReplyRules.isListingCompleted(client.getReplyCode())) IoResult.Success(identifiedListing(lines, parser, received, path))
                else FtpReplyRules.dataCommandRefusal(request.command.step, client.getReplyString())
        }
    }

    /**
     * 끝까지 받은 목록의 줄을 받은 순서대로 해석해 항목으로 바꾸고, 서버가 알려준 식별값을 항목 경로에 묶는다.
     * 해석하지 못한 줄은 버리지 않고 [ListedItem.Unreadable]로 남긴다 — 버리면 목록에서 항목이 말없이 빠진다(#255).
     *
     * 알려진 문제(방치하기로 결정): 목록은 서버가 드러내는 항목뿐이라(RFC 3659 §7.1, 유닉스 계열 LIST의 `.` 항목·ProFTPD `HideFiles` 등)
     * 서버가 숨긴 항목이 빠질 수 있지만 폴더의 목록으로 보고한다. 그래서 업로드·이름 변경의 충돌 검사가 숨긴 같은 이름 항목을 보지 못해
     * STOR·RNTO가 묻지 않고 덮어쓸 수 있다. 표준 명령으로는 항목이 없음을 증명할 수 없다(MLST·SIZE의 550은 없음과 거부를 구분하지 않는다).
     * @param lines 받은 항목 줄
     * @param parser 목록 줄 해석기
     * @param received 해석한 항목이 디렉터리 안의 항목이면 그 항목과 식별값을, 아니면 null을 돌려주는 동작
     * @param path 항목 경로를 만들 ftp://server/path 형식의 디렉터리 경로
     * @return 받은 목록과 식별값
     */
    private fun identifiedListing(
        lines: List<String>,
        parser: FTPFileEntryParser,
        received: (FTPFile) -> ReceivedEntry?,
        path: String
    ): IdentifiedListing {
        val identities = HashMap<String, String>()
        val items = lines.mapNotNull { line ->
            val file = FtpListingFormat.parsedEntry(parser, line) ?: return@mapNotNull ListedItem.Unreadable(path, line)
            val entry = received(file) ?: return@mapNotNull null
            val converted = toFileEntry(entry.file, path)
            entry.identity?.let { identities[converted.path] = it }
            ListedItem.Entry(converted)
        }
        return IdentifiedListing(items, identities)
    }

    /**
     * MLSD로 받은 항목의 사실을 한 번 읽어, 나열한 디렉터리 자신·상위 항목(type=cdir/pdir)을 걸러낸다.
     * 걸러내지 않으면 트리 수집이 자기 자신을 하위 디렉터리로 보고 내려간다.
     * @param file MLSD로 받은 항목
     * @param withIdentities 식별값을 붙일지 여부. 식별값은 트리 수집만 쓴다
     * @return 디렉터리 안의 항목과 식별값, 자신·상위 항목이면 null
     */
    private fun receivedMlsdEntry(file: FTPFile, withIdentities: Boolean): ReceivedEntry? {
        val facts = FtpListingFormat.mlsdFacts(file)
        return if (FtpListingFormat.isMlsdSelfOrParent(facts)) null
        else ReceivedEntry(file, if (withIdentities) FtpListingFormat.mlsdIdentity(facts) else null)
    }

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
}
