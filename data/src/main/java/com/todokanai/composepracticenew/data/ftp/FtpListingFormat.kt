package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.parser.DefaultFTPFileEntryParserFactory
import org.apache.commons.net.ftp.parser.ParserInitializationException

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
     * LIST 목록에 숨김 항목을 담도록 `-a` 옵션을 붙일지 정해 [client]에 설정한다. MLSD를 쓰는 서버에는 LIST를 보내지 않으므로 붙이지 않는다.
     * 옵션 없는 LIST는 유닉스 계열 서버가 `.`으로 시작하는 항목을 빼고 보내, 다운로드·삭제할 하위 트리에서 그 항목이 빠진다.
     * `-a`는 FTP 규격(RFC 959)에 없어 서버가 경로로 읽고 거부하거나 다른 목록을 돌려줄 수 있으므로, 홈 디렉터리를 옵션 없이·옵션을 붙여
     * 한 번씩 조회해 옵션을 붙인 목록이 옵션 없는 목록에 숨김 항목만 더한 것일 때만 붙인다.
     * 판정할 수 없으면(홈 경로·목록을 받지 못함, 두 목록이 모두 빔) 붙이지 않는다 — 숨김 항목이 빠질 수 있는 기존 동작이며 연결은 실패로 보지 않는다.
     * commons-net은 이 설정을 연결이 바뀌어도 유지하므로 연결할 때마다 정한다. [isReadable]이 해석기를 정한 뒤에 불러야 한다.
     * @param client 로그인했고 [isReadable] 확인을 통과한 FTPClient
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    fun configureHiddenListing(client: FTPClient) {
        client.listHiddenFiles = false
        if (usesMlsd(client)) return
        client.listHiddenFiles = showsHiddenWithListOption(client)
    }

    /**
     * 홈 디렉터리를 옵션 없이·`-a`를 붙여 조회해, 이 서버가 `-a`를 숨김 항목을 더하라는 뜻으로 받아들이는지 판별한다.
     * 이후 조회와 같은 형태(`LIST -a <경로>`)로 보내도록 경로를 붙여 조회한다.
     * @param client 옵션을 붙이지 않게 설정된 FTPClient. 돌아갈 때도 옵션을 붙이지 않게 설정돼 있다
     * @return `-a`를 붙여도 되면 true
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    private fun showsHiddenWithListOption(client: FTPClient): Boolean {
        val home = client.printWorkingDirectory() ?: return false
        val plain = probeEntryNames(client, home) ?: return false
        client.listHiddenFiles = true
        val withOption = try {
            probeEntryNames(client, home)
        } finally {
            client.listHiddenFiles = false
        }
        return withOption != null && addsOnlyHiddenEntries(plain, withOption)
    }

    /**
     * [client]의 현재 옵션으로 [path]를 LIST 조회해 항목 이름을 돌려준다.
     * @param client 로그인한 FTPClient
     * @param path 조회할 디렉터리의 FTP 경로
     * @return 항목 이름 집합. 서버가 목록을 돌려주지 않았으면 null
     * @throws java.io.IOException 응답을 읽지 못한 경우
     */
    private fun probeEntryNames(client: FTPClient, path: String): Set<String>? {
        val files = client.listFiles(path)
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
}
