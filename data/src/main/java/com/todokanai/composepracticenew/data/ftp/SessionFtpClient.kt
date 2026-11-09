package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import java.io.IOException
import java.io.InputStream

/**
 * 세션이 쓰는 FTPClient. 명령 블록이 쓰는 표면을 구현하고, 목록 조회의 데이터 스트림을 열어 주며, 이 연결의 현재 디렉터리를 기록한다.
 * 전송의 `retrieveFileStream`에 해당하는 목록 조회용 공개 함수가 commons-net에 없어 스트림 열기를 여기에 둔다.
 * commons-net의 `listFiles`·`mlistDir`는 데이터 연결을 읽다 실패한 것과 완료 응답을 읽다 실패한 것을 같은 예외로 던진다.
 * LIST는 완료 응답을 읽지 않은 채 던지고, MLSD는 읽은 뒤에도 던진다. 그래서 목록도 전송처럼 데이터 스트림을 직접 열어 [DataCommandRunner]로 다룬다.
 * 현재 디렉터리는 이동을 보내는 이 객체가 기록하며, 세션의 ioMutex를 쥔 쪽만 읽고 쓴다.
 * 명령 블록에는 [FtpCommandClient]로만 넘긴다.
 */
internal open class SessionFtpClient : FTPClient(), FtpCommandClient {

    /** 이 연결의 현재 디렉터리. [changeDirectory]와 [recordLogin]만 바꾼다. */
    final override var workingDirectory: String? = null
        private set

    /** 이 연결의 로그인 직후 디렉터리. [recordLogin]만 바꾼다. */
    final override var homeDirectory: String? = null
        private set

    /**
     * 새 연결의 로그인 직후 디렉터리를 기록한다. 새 연결의 현재 디렉터리는 홈이다.
     * @param home 로그인 직후의 현재 디렉터리(FTP 경로). 서버가 알려주지 않았으면 null
     */
    fun recordLogin(home: String?) {
        homeDirectory = home
        workingDirectory = home
    }

    /** 이동이 받아들여졌을 때만 현재 디렉터리를 기록한다. */
    final override fun changeDirectory(pathname: String): Boolean =
        changeWorkingDirectory(pathname).also { changed -> if (changed) workingDirectory = pathname }

    /** MLST를 보내고 응답을 끝까지 읽는다. */
    override fun sendMlst(pathname: String): Int = sendCommand(FTPCmd.MLST, pathname)

    /**
     * 인자 없는 LIST를 보내 현재 디렉터리 목록의 데이터 스트림을 연다. 인자를 붙이지 않는 이유는 [FtpCommandExecutor]의 LIST 조회에 있다.
     * @return 서버가 전송을 시작(1xx)하면 데이터 스트림, 데이터 연결을 열기 전에 거부하면 null(거부 응답은 [getReplyCode]에 남는다)
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    override fun openListStream(): InputStream? = openStream(FTPCmd.LIST, argument = null)

    /**
     * MLSD를 보내 [pathname] 목록의 데이터 스트림을 연다.
     * @param pathname 조회할 디렉터리의 FTP 경로
     * @return 서버가 전송을 시작(1xx)하면 데이터 스트림, 데이터 연결을 열기 전에 거부하면 null(거부 응답은 [getReplyCode]에 남는다)
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    override fun openMlsdStream(pathname: String): InputStream? = openStream(FTPCmd.MLSD, pathname)

    /**
     * [command]를 보내 데이터 연결을 열고 그 입력 스트림을 돌려준다. 스트림을 닫으면 데이터 소켓도 닫힌다.
     * @param command 보낼 목록 조회 명령
     * @param argument 명령 인자. 없으면 null
     * @return 데이터 스트림, 서버가 거부하면 null
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    private fun openStream(command: FTPCmd, argument: String?): InputStream? {
        val socket = _openDataConnection_(command, argument) ?: return null
        return try {
            socket.getInputStream()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
    }
}
