package com.todokanai.composepracticenew.data.ftp

import java.io.Closeable
import java.io.IOException

/**
 * 데이터 연결을 쓰는 명령의 데이터 스트림을 열어 본 결과([FtpSession.openDataConnection]). 어느 결과든 제어 채널의 응답은 끝까지 읽은 것이다.
 * 응답을 읽지 못했거나 명령을 보낸 뒤에 실패한 것은 결과가 아니라 예외로 전파된다.
 */
internal sealed interface DataConnectionOpening<out S : Closeable> {

    /**
     * 서버가 전송을 시작해 데이터 연결이 열렸다.
     * @param connection 열린 데이터 연결
     */
    data class Opened<S : Closeable>(val connection: DataConnection<S>) : DataConnectionOpening<S>

    /** 서버가 데이터 연결을 열기 전에 명령을 거부했다(PASV·REST 실패, 명령의 1xx 아닌 응답). 거부 응답은 FTPClient에 남아 있다. */
    data object Refused : DataConnectionOpening<Nothing>

    /**
     * 수동 모드 데이터 소켓을 서버가 알려준 주소에 연결하지 못했다(방화벽·NAT가 막은 경우 등).
     * commons-net은 데이터 소켓을 연결한 뒤에야 명령을 보내므로(`FTPClient._openDataConnection_`), 명령은 보내지 않았고 기다릴 응답도 없다.
     * @param cause 연결하지 못한 실패
     */
    data class NotConnected(val cause: IOException) : DataConnectionOpening<Nothing>
}

/**
 * 데이터 연결을 쓰는 명령(전송·목록 조회) 하나가 연 데이터 연결. 닫는 방법을 데이터를 끝까지 주고받은 경우([finish])와 중단한 경우([abort])로 나눈다.
 * stream 모드에서 데이터 연결을 닫는 것은 파일 끝을 알리는 신호이므로(RFC 959 §3.4.1), 중단을 같은 닫기로 알리면 서버는 잘린 업로드를 다 받은 파일로 저장한다.
 * @param stream 명령이 연 데이터 스트림
 * @param sockets [stream]을 열며 만든 데이터 소켓([FtpSession.openDataConnection])
 */
internal class DataConnection<S : Closeable>(val stream: S, private val sockets: List<WriteBoundedSocket>) {

    /**
     * 전송을 끝까지 마친 데이터 연결을 닫는다. 남은 데이터를 다 보낸 뒤 연결을 닫아 파일 끝을 알린다.
     * @throws java.io.IOException 남은 데이터를 보내지 못한 경우 — 파일 끝을 알리지 못했으므로 전송 실패다
     */
    fun finish() = stream.close()

    /** 전송을 중단한 데이터 연결을 닫는다. 보내지 못한 데이터를 버리고 연결을 리셋해, 서버가 중단을 파일 끝과 구분하게 한다. */
    fun abort() {
        sockets.forEach { it.abort() }
        // 소켓을 먼저 리셋했으므로 스트림을 닫으며 남은 버퍼를 보내려는 쓰기는 실패하고 버려진다
        runCatching { stream.close() }
    }
}
