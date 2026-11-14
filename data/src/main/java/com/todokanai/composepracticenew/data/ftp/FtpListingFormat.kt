package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPFileEntryParser
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory
import org.apache.commons.net.ftp.parser.ParserInitializationException
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale

/**
 * 서버의 목록을 어떤 명령으로 조회하고 해석할 수 있는지 판별한다.
 * 연결할 때의 확인([FtpConnector])과 목록 조회([FtpCommandExecutor])가 같은 판정을 쓰게 해, 연결 확인을 통과한 서버는 어느 폴더든 해석할 수 있게 한다.
 */
internal object FtpListingFormat {

    /**
     * 목록 조회에 MLSD를 쓰는지 판별한다. MLSD 목록은 형식이 정해져 있어(RFC 3659) 서버 종류를 몰라도 해석할 수 있다.
     * commons-net은 FEAT 응답을 연결이 끝날 때까지 기억하므로 같은 연결에서는 언제나 같은 답이다.
     * @param client 로그인한 FTP 클라이언트
     * @return MLSD를 쓰면 true, LIST를 쓰면 false
     */
    fun usesMlsd(client: FtpCommandClient): Boolean = client.hasFeature(FTPCmd.MLST)

    /**
     * 이 서버의 목록을 해석할 수 있는지 확인한다.
     * LIST 목록은 서버마다 형식이 달라 commons-net이 SYST가 알려준 시스템 종류로 해석기를 고른다. 서버가 SYST를 거부하거나
     * 해석기가 없는 종류를 알려주면 어느 폴더도 조회할 수 없다. 형식을 추측하면 다른 형식의 목록을 잘못 읽을 수 있어 추측하지 않는다.
     * 확인을 통과하면 commons-net이 시스템 종류를 연결이 끝날 때까지 기억하므로 이후 조회는 같은 해석기를 쓰고 SYST를 다시 보내지 않는다.
     * @param client 로그인한 FTPClient
     * @return 해석할 수 있으면 true
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    fun isReadable(client: SessionFtpClient): Boolean {
        if (usesMlsd(client)) return true
        if (!FtpReplyRules.isSystemTypeReply(client.syst())) return false
        // syst()는 응답을 기억하지 않는다 — getSystemType이 SYST를 한 번 더 보내 commons-net이 해석기를 고를 때 쓸 값을 기억하게 한다
        return hasParserFor(client.systemType)
    }

    /**
     * MLSD 항목 원본 줄의 사실 목록을 이름 → 값으로 읽는다. 사실 이름은 대소문자를 구분하지 않아(RFC 3659 §7.1) 소문자로 바꾸고, 값은 그대로 둔다.
     * 값에 `=`가 들어갈 수 있으므로(예: `type=OS.unix=slink:...`) 첫 `=`에서 나눈다. commons-net은 `unique` 등 일부 사실을 해석하지 않으므로 원본 줄에서 읽는다.
     * @param file MLSD로 받은 항목
     * @return 사실 이름 → 값. 원본 줄이 없으면 빈 맵
     */
    fun mlsdFacts(file: FTPFile): Map<String, String> =
        file.rawListing?.substringBefore(' ')?.split(';').orEmpty()
            .filter { '=' in it }
            .associate { it.substringBefore('=').lowercase(Locale.ROOT) to it.substringAfter('=') }

    /**
     * 목록 데이터를 [parser]의 규칙으로 항목 줄 단위로 끝까지 읽는다. commons-net `FTPListParseEngine`과 같은 순서(줄 읽기 → `preParse`)다.
     * 엔진의 `getFiles`는 해석하지 못한 줄을 말없이 버리므로, 줄을 직접 받아 [parsedEntry]로 하나씩 해석한다(#255).
     * 스트림은 닫지 않는다 — 데이터 연결을 어떻게 닫을지(정상·중단)는 [DataCommandRunner]가 정한다.
     * @param stream 목록 데이터 스트림
     * @param encoding 목록의 문자 인코딩(제어 채널과 같다)
     * @param parser 줄을 나누고 거를 해석기. LIST는 시스템 종류의 해석기, MLSD는 MLSx 해석기다
     * @return 해석할 항목 줄(`total` 줄처럼 해석기가 거르는 줄은 빠진다)
     * @throws java.io.IOException 데이터를 받다 실패한 경우
     */
    fun readLines(stream: InputStream, encoding: String, parser: FTPFileEntryParser): List<String> {
        val reader = BufferedReader(InputStreamReader(stream, Charset.forName(encoding)))
        return parser.preParse(generateSequence { parser.readNextEntry(reader) }.toMutableList())
    }

    /**
     * 목록 줄 하나를 [parser]로 해석한다. commons-net 해석기가 값 때문에 예외를 던지는 줄(숫자가 아닌 size, 짧은 unix.mode)도 해석하지 못한 줄이다 —
     * 예외를 그대로 두면 줄 하나 때문에 목록 전체가 구현 오류로 실패한다.
     * @param parser 목록 해석기
     * @param line 목록 줄
     * @return 해석한 항목, 해석하지 못하면 null
     */
    fun parsedEntry(parser: FTPFileEntryParser, line: String): FTPFile? = try {
        parser.parseFTPEntry(line)
    } catch (e: NumberFormatException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    /**
     * MLSD 항목이 나열한 디렉터리 자신(type=cdir)이나 상위 디렉터리(type=pdir)인지 판별한다. 종류 값은 대소문자를 구분하지 않는다(RFC 3659 §7.5.1).
     * commons-net은 두 종류를 일반 디렉터리로 파싱하고, 서버에 따라 이름이 "."이 아닌 전체 경로로 오므로 원본 줄의 type 사실로 판별한다.
     * @param facts [mlsdFacts]로 읽은 사실
     * @return 자신 항목이나 상위 항목이면 true, 디렉터리 안의 항목이면 false
     */
    fun isMlsdSelfOrParent(facts: Map<String, String>): Boolean = facts[FACT_TYPE]?.lowercase(Locale.ROOT) in SELF_OR_PARENT_TYPES

    /**
     * MLSD 항목의 `unique` 사실을 꺼낸다(RFC 3659 §7.5.2). 같은 디렉터리를 가리키는 항목은 경로가 달라도 값이 같다 —
     * 디렉터리로 보고된 링크는 링크 대상의 값을 가진다(ProFTPD·Pure-FTPd는 대상의 장치·inode 번호). 값은 대소문자를 구분한다.
     * 서버가 이미 목록에 담아 보내는 값만 쓰고, 담도록 요청하지 않는다(`OPTS MLST`) — 트리 수집이 끝나는 것은 메모리 상한이 보장하므로
     * 이 값은 순환을 더 일찍 알리는 데만 쓴다.
     * @param facts [mlsdFacts]로 읽은 사실
     * @return 식별값. 서버가 알려주지 않았으면 null
     */
    fun mlsdIdentity(facts: Map<String, String>): String? = facts[FACT_UNIQUE]?.takeIf { it.isNotEmpty() }

    /**
     * LIST 목록에 쓸 해석기를 [systemType]으로 만들 수 있는지 확인한다.
     * 목록 조회가 쓰는 것과 같은 해석기([listParser])로 확인한다.
     * @param systemType SYST가 알려준 시스템 종류(예: "UNIX Type: L8")
     * @return 해석기를 만들 수 있으면 true
     */
    private fun hasParserFor(systemType: String): Boolean =
        try {
            parserFor(systemType)
            true
        } catch (e: ParserInitializationException) {
            false
        }

    /**
     * LIST 목록을 해석할 해석기를 만든다. 연결할 때 [isReadable]이 확인한 것과 같은 해석기다.
     * commons-net은 [isReadable]이 알아낸 시스템 종류를 연결이 끝날 때까지 기억하므로 SYST를 다시 보내지 않는다.
     * @param client [isReadable] 확인을 통과한 FTP 클라이언트
     * @return LIST 목록 해석기
     */
    fun listParser(client: FtpCommandClient): FTPFileEntryParser = parserFor(client.getSystemType())

    /**
     * [systemType]의 LIST 목록 해석기를 만든다. 이 앱은 해석기 팩토리를 바꾸지 않으므로 commons-net의 기본 팩토리를 쓴다.
     * @param systemType SYST가 알려준 시스템 종류
     * @return LIST 목록 해석기
     * @throws ParserInitializationException 해석기가 없는 시스템 종류인 경우
     */
    private fun parserFor(systemType: String): FTPFileEntryParser = DefaultFTPFileEntryParserFactory().createFileEntryParser(systemType)

    /** MLSD 항목 종류 사실의 이름. */
    private const val FACT_TYPE = "type"
    /** MLSD 항목 식별값 사실의 이름. */
    private const val FACT_UNIQUE = "unique"
    /** 나열한 디렉터리 자신(cdir)이나 상위 디렉터리(pdir)를 뜻하는 MLSD 항목 종류. */
    private val SELF_OR_PARENT_TYPES = setOf("cdir", "pdir")
}
