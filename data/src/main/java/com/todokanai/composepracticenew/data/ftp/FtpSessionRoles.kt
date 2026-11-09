package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/** FTP 세션의 연결 상태를 읽기만 하는 역할. */
internal interface FtpConnectionStatus {

    /** FTP 서버와의 연결 상태. */
    val connectionState: StateFlow<RemoteConnectionState>

    /** 연결·해제 작업이 진행 중이면 true를 방출한다. */
    val isConnecting: StateFlow<Boolean>
}

/** 연결된 FTP 세션에 명령을 보내는 역할. 명령 실패의 의미는 [withConnectedClient] 한 곳이 정한다. */
internal interface FtpCommandSession {

    /**
     * 연결된 상태에서만 [block]을 실행한다. 결과와 실패의 의미는 [FtpSession.withConnectedClient]에 있다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 결과, 또는 연결 끊김
     */
    suspend fun <T> withConnectedClient(block: suspend (FtpCommandClient) -> IoResult<T>): IoResult<T>

    /**
     * 데이터 연결을 쓰는 명령의 데이터 스트림을 연다. [withConnectedClient]의 블록 안에서 호출한다.
     * @param open 데이터 스트림을 여는 명령. 서버가 거부하면 null을 돌려준다
     * @return 열린 데이터 연결, 서버가 거부했으면 null
     */
    fun <S : Closeable> openDataConnection(open: () -> S?): DataConnection<S>?

    /** 응답 순서를 알 수 없게 된 세션을 끝낸다. [withConnectedClient]의 블록 안에서 호출한다. */
    suspend fun endUnusableSession()
}

/** FTP 세션의 연결 수명(접속·로그인·종료)을 다루는 역할. 연결된 세션의 명령은 [FtpCommandSession]으로 보낸다. */
internal interface FtpConnectionLifecycle : FtpConnectionStatus {

    /**
     * 연결 상태를 확인하지 않고 잠금 안에서 [block]에 FTPClient를 넘긴다. 접속·로그인에만 쓴다.
     * @param block 잠금 안에서 실행할 연결 작업
     * @return block의 반환값
     */
    suspend fun <T> withClient(block: suspend (ListingStreamFtpClient) -> T): T

    /**
     * 소켓이 열려 있는지 판별한다.
     * @return 소켓이 열려 있으면 true
     */
    fun isSocketOpen(): Boolean

    /**
     * 새 연결 작업을 시작한다.
     * @return 연결 작업이 [markLoggedIn]에 넘길 종료 요청 표지
     */
    suspend fun beginConnecting(): Int

    /** 연결 작업이 끝났음을 기록한다. */
    suspend fun endConnecting()

    /**
     * 로그인 성공 상태로 전환한다.
     * @param closeMark 연결 작업을 시작할 때 읽은 종료 요청 표지
     * @param home 로그인 직후의 현재 디렉터리(FTP 경로). 서버가 알려주지 않았으면 null
     * @param serverName 연결한 서버의 표시 이름
     * @return 전환했으면 true, 그 사이 종료가 요청돼 전환하지 않았으면 false
     */
    suspend fun markLoggedIn(closeMark: Int, home: String?, serverName: String): Boolean

    /** 로그인 해제 상태로 전환한다. */
    suspend fun markLoggedOut()

    /** 진행 중인 FTP 명령을 기다리지 않고 연결을 끊는다. */
    suspend fun closeBypassingIoLock()

    /**
     * 연결된 세션만 끊김으로 남기며 닫는다.
     * @return 연결된 세션을 끝냈으면 true
     */
    suspend fun dropConnected(): Boolean
}
