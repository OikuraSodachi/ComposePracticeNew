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
