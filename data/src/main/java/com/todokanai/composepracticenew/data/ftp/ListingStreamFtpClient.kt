package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import java.io.IOException
import java.io.InputStream

/**
 * 목록 조회 명령(LIST·MLSD)의 데이터 스트림을 열어 주는 FTPClient. 전송의 `retrieveFileStream`에 해당하는 목록 조회용 공개 함수가 commons-net에 없어 둔다.
 * commons-net의 `listFiles`·`mlistDir`는 데이터 연결을 읽다 실패한 것과 완료 응답을 읽다 실패한 것을 같은 예외로 던진다.
 * LIST는 완료 응답을 읽지 않은 채 던지고, MLSD는 읽은 뒤에도 던진다. 그래서 목록도 전송처럼 데이터 스트림을 직접 열어 [DataCommandRunner]로 다룬다.
 */
internal open class ListingStreamFtpClient : FTPClient() {

    /**
     * 인자 없는 LIST를 보내 현재 디렉터리 목록의 데이터 스트림을 연다. 인자를 붙이지 않는 이유는 [FtpCommandExecutor]의 LIST 조회에 있다.
     * @return 서버가 전송을 시작(1xx)하면 데이터 스트림, 데이터 연결을 열기 전에 거부하면 null(거부 응답은 [getReplyCode]에 남는다)
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    open fun openListStream(): InputStream? = openStream(FTPCmd.LIST, argument = null)

    /**
     * MLSD를 보내 [pathname] 목록의 데이터 스트림을 연다.
     * @param pathname 조회할 디렉터리의 FTP 경로
     * @return 서버가 전송을 시작(1xx)하면 데이터 스트림, 데이터 연결을 열기 전에 거부하면 null(거부 응답은 [getReplyCode]에 남는다)
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    open fun openMlsdStream(pathname: String): InputStream? = openStream(FTPCmd.MLSD, pathname)

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
