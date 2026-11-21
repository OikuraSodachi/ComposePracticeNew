package com.todokanai.composepracticenew.data.ftp

import java.io.IOException

/**
 * FTP 세션 하나를 열 때 쓰는 서버 접속 정보.
 * @param address 서버 호스트명 또는 IP (ftp:// 접두사 포함 가능)
 * @param port FTP 포트
 * @param userId FTP 로그인 아이디
 * @param password FTP 로그인 비밀번호
 * @param encoding 컨트롤 채널 문자 인코딩 (예: UTF-8, EUC-KR)
 */
internal class FtpEndpoint(val address: String, val port: Int, val userId: String, val password: String, val encoding: String)

/** FTP 세션을 연 결과([FtpSession.open]). 연 쪽(탐색 연결·작업 연결)이 자기 결과 형식으로 바꾼다. */
internal sealed interface SessionOpening {

    /** 접속·로그인·목록 형식 확인을 마쳐 명령을 보낼 수 있다. */
    data object Opened : SessionOpening

    /**
     * 서버가 세션을 거부했다 — 접속 인사의 421(RFC 959 §5.4)이나 로그인 거부(530 등).
     * @param reply 서버가 보낸 거부 응답 원문
     */
    data class Refused(val reply: String) : SessionOpening

    /**
     * 로그인했지만 서버가 바이너리 전송 방식(TYPE I)을 거부했다. RFC 1123 §4.1.2.2가 모든 서버에 요구하는 방식이라 지원 범위 밖의 서버다 —
     * 거부를 무시하면 ASCII 방식에 남아 전송이 줄바꿈을 바꿔 파일이 손상된 채 성공으로 끝난다(#423).
     * @param reply 서버가 보낸 거부 응답 원문
     */
    data class BinaryTypeRefused(val reply: String) : SessionOpening

    /** 로그인했지만 이 서버의 목록을 해석할 수 없다([FtpListingFormat.isReadable]). */
    data object ListingFormatUnknown : SessionOpening

    /**
     * 서버에 접속하지 못했거나 응답을 읽지 못했다.
     * @param cause 접속·응답 읽기에서 난 예외
     */
    data class Unreachable(val cause: IOException) : SessionOpening

    /** 여는 동안 세션 종료([FtpSession.close])가 요청됐다. 어느 단계에서 끝났든 열린 것으로 보지 않는다. */
    data object Closed : SessionOpening
}
