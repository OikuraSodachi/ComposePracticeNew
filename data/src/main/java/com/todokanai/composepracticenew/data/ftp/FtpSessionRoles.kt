package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import java.io.Closeable

/** 연결된 FTP 세션에 명령을 보내는 역할. 명령 실패의 의미는 [withConnectedClient] 한 곳이 정한다. */
internal interface FtpCommandSession {

    /**
     * 연결된 상태에서만 [block]을 실행한다. 결과와 실패의 의미는 [FtpSession.withConnectedClient]에 있다.
     * @param block 잠금 안에서 실행할 FTP 명령
     * @return block의 결과, 또는 연결 끊김
     */
    suspend fun <T> withConnectedClient(block: suspend (FtpCommandClient) -> IoResult<T>): IoResult<T>
}

/** 데이터 연결을 쓰는 명령([DataCommandRunner])이 세션에 요구하는 역할. 모두 잠금을 쥔 [FtpCommandSession.withConnectedClient] 블록 안에서 호출한다. */
internal interface FtpDataCommandSession {

    /**
     * 데이터 연결을 쓰는 명령의 데이터 스트림을 연다.
     * @param open 데이터 스트림을 여는 명령. 서버가 거부하면 null을 돌려준다
     * @return 열린 데이터 연결, 서버의 거부, 또는 데이터 소켓을 연결하지 못함
     */
    fun <S : Closeable> openDataConnection(open: () -> S?): DataConnectionOpening<S>

    /** 응답 순서를 알 수 없게 된 세션을 끝낸다. */
    suspend fun endUnusableSession()

    /**
     * 세션이 연결·로그인된 상태인지 판별한다.
     * @return 명령을 보낼 수 있는 세션이면 true
     */
    suspend fun isReady(): Boolean
}
