package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory
import org.apache.commons.net.ftp.parser.ParserInitializationException
import java.io.IOException

/** LIST에 숨김 항목을 담는 `-a` 옵션을 붙일지 판정한 결과([FtpListingFormat.probeHiddenListing]). */
internal sealed interface HiddenListingProbe {
    /**
     * 판정을 마쳤고 연결은 그대로 쓸 수 있다. 판정할 근거가 없었던 경우도 붙이지 않는 것으로 마친다.
     * @param useListOption LIST에 `-a`를 붙이면 true
     */
    data class Decided(val useListOption: Boolean) : HiddenListingProbe

    /** 판정 중 응답을 읽지 못했다(연결 닫힘·421·타임아웃). 받지 못한 응답이 남았을 수 있어 이 연결은 더 쓸 수 없다. */
    data object ConnectionBroken : HiddenListingProbe
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
     * LIST 목록에 숨김 항목을 담도록 `-a` 옵션을 붙일지 판정한다. MLSD를 쓰는 서버에는 LIST를 보내지 않으므로 붙이지 않는다.
     * 옵션 없는 LIST는 유닉스 계열 서버가 `.`으로 시작하는 항목을 빼고 보내, 다운로드·삭제할 하위 트리에서 그 항목이 빠진다.
     * `-a`는 FTP 규격(RFC 959)에 없어 서버가 경로로 읽고 거부하거나 다른 목록을 돌려줄 수 있으므로, 현재 디렉터리를 옵션 없이·옵션을 붙여
     * 한 번씩 조회해 옵션을 붙인 목록이 옵션 없는 목록에 숨김 항목만 더한 것일 때만 붙인다.
     * 목록 조회([FtpCommandExecutor])는 디렉터리로 이동한 뒤 [listCurrentDirectory]로 조회하므로, 이 판정은 이후 조회와 같은 명령(`LIST`·`LIST -a .`)을 검증한다.
     * 판정은 연결을 실패로 만들지 않는다. 판정할 근거가 없으면(목록을 받지 못함, 데이터 연결을 열지 못함, 두 목록이 모두 빔) 붙이지 않는 것으로 마치고,
     * 제어 연결을 더 쓸 수 없게 되면 그 사실을 결과로 알린다 — 그 연결을 어떻게 할지는 연결을 맡은 쪽([FtpConnector])이 정한다.
     * commons-net은 옵션 설정을 연결이 바뀌어도 유지하므로 판정 전에 끈다. [isReadable]이 해석기를 정한 뒤에 불러야 한다.
     * @param client 로그인했고 [isReadable] 확인을 통과한 FTPClient. 돌아갈 때는 옵션을 붙이지 않게 설정돼 있다
     * @return 판정 결과
     */
    fun probeHiddenListing(client: FTPClient): HiddenListingProbe {
        client.listHiddenFiles = false
        return watchingReplies(client) { watch ->
            try {
                HiddenListingProbe.Decided(!usesMlsd(client) && showsHiddenWithListOption(client))
            } catch (e: IOException) {
                if (isControlChannelUnusable(e, watch)) HiddenListingProbe.ConnectionBroken else HiddenListingProbe.Decided(useListOption = false)
            }
        }
    }

    /**
     * 현재 디렉터리를 [client]의 옵션대로 LIST 조회한다. 옵션이 없으면 인자 없이(`LIST`), `-a`를 쓰면 현재 디렉터리 경로를 덧붙여(`LIST -a .`) 보낸다.
     * LIST 인자는 RFC 959상 경로뿐이라 서버마다 옵션을 알아보는 방식이 다르다. ProFTPD는 인자가 하나면 그 이름의 항목이 있을 때 경로로 읽어
     * `-a`라는 항목이 있는 폴더에서는 그 항목만 나열하지만, 인자가 둘이면 언제나 옵션으로 읽는다. 덧붙인 경로가 현재 디렉터리이므로
     * 경로를 무시하는 서버도 같은 폴더를 나열한다. 연결할 때의 판정([probeHiddenListing])과 목록 조회([FtpCommandExecutor])가 이 함수로 같은 명령을 보낸다.
     * @param client 조회할 디렉터리로 이동한 FTPClient
     * @return 조회한 항목. 서버가 목록을 돌려주지 않았으면 빈 배열이며, 결과는 응답 코드로 판별한다
     * @throws java.io.IOException 데이터 연결을 열지 못했거나 응답을 읽지 못한 경우
     */
    fun listCurrentDirectory(client: FTPClient): Array<FTPFile> = client.listFiles(if (client.listHiddenFiles) CURRENT_DIRECTORY else null)

    /**
     * 현재 디렉터리를 옵션 없이·`-a`를 붙여 조회해, 이 서버가 `-a`를 숨김 항목을 더하라는 뜻으로 받아들이는지 판별한다.
     * @param client 옵션을 붙이지 않게 설정된 FTPClient. 돌아갈 때도 옵션을 붙이지 않게 설정돼 있다
     * @return `-a`를 붙여도 되면 true
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    private fun showsHiddenWithListOption(client: FTPClient): Boolean {
        val plain = probeEntryNames(client) ?: return false
        client.listHiddenFiles = true
        val withOption = try {
            probeEntryNames(client)
        } finally {
            client.listHiddenFiles = false
        }
        return withOption != null && addsOnlyHiddenEntries(plain, withOption)
    }

    /**
     * [client]의 현재 옵션으로 현재 디렉터리를 LIST 조회([listCurrentDirectory])해 항목 이름을 돌려준다.
     * @param client 로그인한 FTPClient
     * @return 항목 이름 집합. 서버가 목록을 돌려주지 않았으면 null
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    private fun probeEntryNames(client: FTPClient): Set<String>? {
        val files = listCurrentDirectory(client)
        if (!FtpReplyRules.isListingProbeCompleted(client.replyCode)) return null
        return files.mapNotNullTo(HashSet()) { it?.name }
    }

    /**
     * `-a`를 붙인 목록이 옵션 없는 목록에 숨김 항목만 더한 것인지 판별한다.
     * 옵션 없는 목록의 항목이 빠졌거나 숨김이 아닌 항목이 더해졌으면 서버가 `-a`를 다른 뜻(예: 경로)으로 읽은 것으로 본다.
     * 두 목록이 모두 비었으면 `-a`를 경로로 읽고 빈 목록을 돌려준 서버와 구분할 근거가 없다.
     * @param plain 옵션 없이 받은 항목 이름
     * @param withOption `-a`를 붙여 받은 항목 이름
     * @return 숨김 항목만 더했으면 true
     */
    private fun addsOnlyHiddenEntries(plain: Set<String>, withOption: Set<String>): Boolean {
        val added = withOption - plain
        return withOption.containsAll(plain) && added.all { it.startsWith('.') } && (plain.isNotEmpty() || added.isNotEmpty())
    }

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
     * 수동 모드는 데이터 연결을 먼저 연 뒤 LIST를 보내므로, 데이터 연결을 열지 못한 실패는 모든 명령의 응답을 받은 뒤라 제어 연결이 멀쩡하다.
     * @param cause 판정 중 난 예외
     * @param watch 판정 동안 명령과 응답을 지켜본 수신기
     * @return 제어 연결을 더 쓸 수 없으면 true
     */
    private fun isControlChannelUnusable(cause: IOException, watch: FtpReplyWatch): Boolean =
        cause is FTPConnectionClosedException || watch.awaitingFinalReply

    /** `-a`와 함께 보내는 현재 디렉터리 경로([listCurrentDirectory]). */
    private const val CURRENT_DIRECTORY = "."
}
