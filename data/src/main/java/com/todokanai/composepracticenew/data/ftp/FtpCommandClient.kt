package com.todokanai.composepracticenew.data.ftp

import org.apache.commons.net.ftp.FTPCmd
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 연결된 세션의 명령 블록([FtpCommandSession.withConnectedClient])이 쓸 수 있는 FTP 명령만 담은 표면.
 * 연결 수명(로그인·종료)과 기록 없는 디렉터리 이동을 FTPClient 원본처럼 부를 수 없게, 명령 블록에는 이 타입만 넘긴다.
 * 함수 이름은 구현([SessionFtpClient])이 commons-net에서 물려받는 함수와 같다.
 */
internal interface FtpCommandClient {

    /** 세션의 현재 디렉터리(FTP 경로). 로그인 직후에는 [homeDirectory]이고, [changeDirectory]로 옮길 때마다 갱신된다. 알 수 없으면 null이다. */
    val workingDirectory: String?

    /** 로그인 직후의 현재 디렉터리(FTP 경로). 대상 밖으로 나가는 이동이 거부될 때 다시 시도할 곳이다. 서버가 알려주지 않았으면 null이다. */
    val homeDirectory: String?

    /** 이 연결에 마지막으로 보낸 명령 이름(예: "PASV"). 데이터 스트림을 열다 거부됐을 때 그 앞의 준비 명령까지 포함해 어느 명령이 거부됐는지 알려 준다. 아직 보낸 명령이 없으면 null이다. */
    val lastSentCommand: String?

    /**
     * 현재 디렉터리를 옮기고, 옮겼으면 [workingDirectory]에 기록한다. 이동과 기록이 어긋나지 않도록 디렉터리 이동은 이 함수로만 한다.
     * @param pathname 옮겨 갈 디렉터리의 FTP 경로
     * @return 옮겼으면 true, 서버가 거부하면 false
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun changeDirectory(pathname: String): Boolean

    /**
     * 서버가 FEAT로 [feature]를 알렸는지 판별한다.
     * @param feature 확인할 기능
     * @return 알렸으면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun hasFeature(feature: FTPCmd): Boolean

    /**
     * 서버의 시스템 종류를 돌려준다.
     * @return 서버가 알려준 시스템 종류
     * @throws IOException 응답을 읽지 못했거나 서버가 거부한 경우
     */
    fun getSystemType(): String

    /**
     * 제어 채널의 문자 인코딩을 돌려준다.
     * @return 인코딩 이름
     */
    fun getControlEncoding(): String

    /**
     * 마지막 응답의 코드를 돌려준다.
     * @return 응답 코드
     */
    fun getReplyCode(): Int

    /**
     * 마지막 응답 전체를 문자열로 돌려준다.
     * @return 응답 문자열
     */
    fun getReplyString(): String

    /**
     * 마지막 응답을 줄 단위로 돌려준다.
     * @return 응답 줄
     */
    fun getReplyStrings(): Array<String>

    /**
     * 인자 없는 LIST의 데이터 스트림을 연다.
     * @return 데이터 스트림, 서버가 거부하면 null
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    fun openListStream(): InputStream?

    /**
     * MLSD의 데이터 스트림을 연다.
     * @param pathname 조회할 디렉터리의 FTP 경로
     * @return 데이터 스트림, 서버가 거부하면 null
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    fun openMlsdStream(pathname: String): InputStream?

    /**
     * RETR의 데이터 스트림을 연다.
     * @param remote 받을 파일의 FTP 경로
     * @return 데이터 스트림, 서버가 거부하면 null
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    fun retrieveFileStream(remote: String): InputStream?

    /**
     * STOR의 데이터 스트림을 연다.
     * @param remote 저장할 파일의 FTP 경로
     * @return 데이터 스트림, 서버가 거부하면 null
     * @throws IOException 응답을 읽지 못했거나 데이터 소켓을 열지 못한 경우
     */
    fun storeFileStream(remote: String): OutputStream?

    /**
     * 데이터 연결을 쓰는 명령의 완료 응답을 읽는다.
     * @return 완료 응답이 성공(2xx)이면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun completePendingCommand(): Boolean

    /**
     * MLST를 보내고 응답을 끝까지 읽는다. 항목 줄은 [getReplyStrings]에 남는다.
     * 임의 명령을 보내는 함수는 두지 않는다 — CWD를 보내면 [workingDirectory]가 서버와 어긋난다.
     * @param pathname 조회할 항목의 FTP 경로
     * @return 응답 코드
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun sendMlst(pathname: String): Int

    /**
     * NOOP을 보낸다.
     * @return 서버가 받아들이면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun sendNoOp(): Boolean

    /**
     * 항목의 이름을 바꾸거나 옮긴다.
     * @param from 원래 FTP 경로
     * @param to 새 FTP 경로
     * @return 서버가 받아들이면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun rename(from: String, to: String): Boolean

    /**
     * 파일을 지운다.
     * @param pathname 지울 파일의 FTP 경로
     * @return 서버가 받아들이면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun deleteFile(pathname: String): Boolean

    /**
     * 빈 디렉터리를 지운다.
     * @param pathname 지울 디렉터리의 FTP 경로
     * @return 서버가 받아들이면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun removeDirectory(pathname: String): Boolean

    /**
     * 디렉터리를 만든다.
     * @param pathname 만들 디렉터리의 FTP 경로
     * @return 서버가 받아들이면 true
     * @throws IOException 응답을 읽지 못한 경우
     */
    fun makeDirectory(pathname: String): Boolean
}
