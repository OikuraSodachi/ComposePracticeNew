package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory
import org.apache.commons.net.ftp.parser.ParserInitializationException
import java.io.IOException
import java.util.Locale

/** 연결할 때 목록 조회 옵션을 정한 결과([FtpListingFormat.negotiateListingOptions]). */
internal sealed interface ListingOptionsProbe {
    /** 옵션을 정했고 연결은 그대로 쓸 수 있다. 서버가 요청을 거부한 경우도 요청 없이 쓰는 것으로 마친다. */
    data object Decided : ListingOptionsProbe

    /** 옵션을 정하는 중 응답을 읽지 못했다(연결 닫힘·421·타임아웃). 받지 못한 응답이 남았을 수 있어 이 연결은 더 쓸 수 없다. */
    data object ConnectionBroken : ListingOptionsProbe
}

/**
 * 서버의 목록을 어떤 명령으로 조회하고 해석할 수 있는지 판별한다.
 * 연결할 때의 확인([FtpConnector])과 목록 조회([FtpCommandExecutor])가 같은 판정을 쓰게 해, 연결 확인을 통과한 서버는 어느 폴더든 해석할 수 있게 한다.
 */
internal object FtpListingFormat {

    /**
     * 목록 조회에 MLSD를 쓰는지 판별한다. MLSD 목록은 형식이 정해져 있어(RFC 3659) 서버 종류를 몰라도 해석할 수 있다.
     * commons-net은 FEAT 응답을 연결이 끝날 때까지 기억하므로 같은 연결에서는 언제나 같은 답이다.
     * @param client 로그인한 FTPClient
     * @return MLSD를 쓰면 true, LIST를 쓰면 false
     */
    fun usesMlsd(client: FTPClient): Boolean = client.hasFeature(FTPCmd.MLST)

    /**
     * 이 서버의 목록을 해석할 수 있는지 확인한다.
     * LIST 목록은 서버마다 형식이 달라 commons-net이 SYST가 알려준 시스템 종류로 해석기를 고른다. 서버가 SYST를 거부하거나
     * 해석기가 없는 종류를 알려주면 어느 폴더도 조회할 수 없다. 형식을 추측하면 다른 형식의 목록을 잘못 읽을 수 있어 추측하지 않는다.
     * 확인을 통과하면 commons-net이 시스템 종류를 연결이 끝날 때까지 기억하므로 이후 조회는 같은 해석기를 쓰고 SYST를 다시 보내지 않는다.
     * @param client 로그인한 FTPClient
     * @return 해석할 수 있으면 true
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    fun isReadable(client: FTPClient): Boolean {
        if (usesMlsd(client)) return true
        if (!FtpReplyRules.isSystemTypeReply(client.syst())) return false
        // syst()는 응답을 기억하지 않는다 — getSystemType이 SYST를 한 번 더 보내 commons-net이 해석기를 고를 때 쓸 값을 기억하게 한다
        return hasParserFor(client.systemType)
    }

    /**
     * 연결할 때 이 세션의 목록 조회 옵션을 정한다. MLSD 서버는 항목 식별값을 요청하고([requestEntryIdentity]), LIST 서버에는 정할 옵션이 없다.
     * LIST에는 옵션을 붙이지 않는다 — 인자는 RFC 959상 경로뿐이고, 목록은 서버가 드러내는 항목이다(RFC 3659 §7.1). 서버가 숨긴 항목(유닉스 계열의 `.` 항목 등)은 지원 범위 밖이다.
     * 정하는 일은 연결을 실패로 만들지 않는다. 제어 연결을 더 쓸 수 없게 되면 그 사실을 결과로 알린다 — 그 연결을 어떻게 할지는 연결을 맡은 쪽([FtpConnector])이 정한다.
     * @param client 로그인했고 [isReadable] 확인을 통과한 FTPClient
     * @return 정한 결과
     */
    fun negotiateListingOptions(client: FTPClient): ListingOptionsProbe {
        if (!usesMlsd(client)) return ListingOptionsProbe.Decided
        return watchingReplies(client) { watch ->
            try {
                requestEntryIdentity(client)
                ListingOptionsProbe.Decided
            } catch (e: IOException) {
                if (isControlChannelUnusable(e, watch)) ListingOptionsProbe.ConnectionBroken else ListingOptionsProbe.Decided
            }
        }
    }

    /**
     * MLSD 목록이 항목 식별값(`unique`, RFC 3659 §7.5.2)을 담도록 요청한다. 트리 수집이 이 값으로 순환을 판별한다.
     * 서버는 FEAT의 MLST 줄에 지원하는 사실을 알리고 지금 목록에 담는 사실에 `*`를 붙인다(§7.8). `unique`를 지원하지만 담지 않으면
     * 지금 담는 사실에 `unique`를 더해 `OPTS MLST`로 요청한다(§7.9) — 요청에 적지 않은 사실은 빠지므로 지금 담는 사실을 모두 다시 적는다.
     * 서버가 거부해도 연결은 그대로 쓴다. 식별값이 없으면 트리 수집은 경로 한도로 끝난다.
     * @param client 로그인했고 MLSD를 쓰는 FTPClient
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    private fun requestEntryIdentity(client: FTPClient) {
        val facts = client.featureValues(FTPCmd.MLST.command)?.firstOrNull()?.split(';')?.filter { it.isNotEmpty() } ?: return
        val enabled = facts.filter { it.endsWith(ENABLED_FACT_MARK) }.map { it.removeSuffix(ENABLED_FACT_MARK) }
        val supportsIdentity = facts.any { it.removeSuffix(ENABLED_FACT_MARK).equals(FACT_UNIQUE, ignoreCase = true) }
        if (!supportsIdentity || enabled.any { it.equals(FACT_UNIQUE, ignoreCase = true) }) return
        client.sendCommand(OPTS_COMMAND, "${FTPCmd.MLST.command} " + (enabled + FACT_UNIQUE).joinToString("") { "$it;" })
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
     * MLSD 항목이 나열한 디렉터리 자신(type=cdir)이나 상위 디렉터리(type=pdir)인지 판별한다. 종류 값은 대소문자를 구분하지 않는다(RFC 3659 §7.5.1).
     * commons-net은 두 종류를 일반 디렉터리로 파싱하고, 서버에 따라 이름이 "."이 아닌 전체 경로로 오므로 원본 줄의 type 사실로 판별한다.
     * @param facts [mlsdFacts]로 읽은 사실
     * @return 자신 항목이면 [MlsdSelfOrParent.SELF], 상위 항목이면 [MlsdSelfOrParent.PARENT], 디렉터리 안의 항목이면 null
     */
    fun mlsdSelfOrParent(facts: Map<String, String>): MlsdSelfOrParent? = when (facts[FACT_TYPE]?.lowercase(Locale.ROOT)) {
        TYPE_CDIR -> MlsdSelfOrParent.SELF
        TYPE_PDIR -> MlsdSelfOrParent.PARENT
        else -> null
    }

    /**
     * MLSD 항목의 `unique` 사실을 꺼낸다(RFC 3659 §7.5.2). 같은 디렉터리를 가리키는 항목은 경로가 달라도 값이 같다 —
     * 디렉터리로 보고된 링크는 링크 대상의 값을 가진다(ProFTPD·Pure-FTPd는 대상의 장치·inode 번호). 값은 대소문자를 구분한다.
     * @param facts [mlsdFacts]로 읽은 사실
     * @return 식별값. 서버가 알려주지 않았으면 null
     */
    fun mlsdIdentity(facts: Map<String, String>): String? = facts[FACT_UNIQUE]?.takeIf { it.isNotEmpty() }

    /**
     * 현재 디렉터리를 인자 없는 `LIST`로 조회한다(RFC 959: 인자가 없으면 현재 디렉터리).
     * 경로 인자를 보내지 않는다 — LIST 인자를 옵션으로도 읽는 서버(ProFTPD 등)가 있어 경로 인자의 해석이 서버마다 갈릴 수 있으므로,
     * 디렉터리로 이동한 뒤 인자 없이 보낸다(curl과 같은 방식). commons-net의 숨김 항목 옵션(`listHiddenFiles`)은 켜지 않으므로 인자 없이 나간다.
     * @param client 조회할 디렉터리로 이동한 FTPClient
     * @return 조회한 항목. 서버가 목록을 돌려주지 않았으면 빈 배열이며, 결과는 응답 코드로 판별한다
     * @throws java.io.IOException 데이터 연결을 열지 못했거나 응답을 읽지 못한 경우
     */
    fun listCurrentDirectory(client: FTPClient): Array<FTPFile> = client.listFiles()

    /**
     * commons-net이 LIST 목록에 쓸 해석기를 [systemType]으로 만들 수 있는지 확인한다.
     * 이 앱은 해석기 팩토리를 바꾸지 않으므로 FTPClient가 쓰는 것과 같은 기본 팩토리로 확인한다.
     * @param systemType SYST가 알려준 시스템 종류(예: "UNIX Type: L8")
     * @return 해석기를 만들 수 있으면 true
     */
    private fun hasParserFor(systemType: String): Boolean =
        try {
            DefaultFTPFileEntryParserFactory().createFileEntryParser(systemType)
            true
        } catch (e: ParserInitializationException) {
            false
        }

    /**
     * 판정 중 난 [cause] 뒤에 제어 연결을 더 쓸 수 없는지 판별한다.
     * 서버가 연결을 닫았거나(421 포함), 보낸 명령의 최종 응답을 받기 전에 실패했으면 늦게 온 응답이 다음 명령의 응답으로 읽혀 순서가 어긋난다.
     * @param cause 판정 중 난 예외
     * @param watch 판정 동안 명령과 응답을 지켜본 수신기
     * @return 제어 연결을 더 쓸 수 없으면 true
     */
    private fun isControlChannelUnusable(cause: IOException, watch: FtpReplyWatch): Boolean =
        cause is FTPConnectionClosedException || watch.awaitingFinalReply

    /** MLSD 목록에 담을 사실을 정하는 명령([requestEntryIdentity]). commons-net의 FTPCmd에 없다. */
    private const val OPTS_COMMAND = "OPTS"
    /** FEAT의 MLST 줄에서 지금 목록에 담는 사실에 붙는 표시(RFC 3659 §7.8). */
    private const val ENABLED_FACT_MARK = "*"
    /** MLSD 항목 종류 사실의 이름. */
    private const val FACT_TYPE = "type"
    /** MLSD 항목 식별값 사실의 이름. */
    private const val FACT_UNIQUE = "unique"
    /** 나열한 디렉터리 자신을 뜻하는 MLSD 항목 종류. */
    private const val TYPE_CDIR = "cdir"
    /** 나열한 디렉터리의 상위 디렉터리를 뜻하는 MLSD 항목 종류. */
    private const val TYPE_PDIR = "pdir"
}

/** MLSD 목록에서 디렉터리 안의 항목이 아닌, 나열한 디렉터리 자신이나 상위 디렉터리를 가리키는 항목의 종류([FtpListingFormat.mlsdSelfOrParent]). */
internal enum class MlsdSelfOrParent {
    /** 나열한 디렉터리 자신(type=cdir). */
    SELF,
    /** 나열한 디렉터리의 상위 디렉터리(type=pdir). */
    PARENT,
}
