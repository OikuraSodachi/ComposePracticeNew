package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

/**
 * 이 앱이 보내는 FTP 명령. 응답 해석 규칙이 명령마다 다를 수 있어 [FtpReplyRules]의 행(行)이 된다.
 * @param label 실패 메시지에 표시할 명령 이름
 */
internal enum class FtpCommand(val label: String) {
    CWD("CWD"),
    LIST("LIST"),
    MLSD("MLSD"),
    RENAME("RENAME"),
    DELE("DELE"),
    MKD("MKD"),
    RMD("RMD"),
    RETR("retrieveFileStream"),
    STOR("storeFileStream"),
}

/** 목록을 조회하는 목적. 목적에 따라 목록에서 읽을 값이 다르다. */
internal enum class FtpListingPurpose {
    /** 화면에 보여줄 목록. */
    BROWSE,
    /** 작업(다운로드·삭제)할 하위 트리 수집. 순환을 판별하려고 서버가 알려준 항목 식별값도 읽는다. */
    TREE,
}

/**
 * FTP 서버 응답을 결과로 해석하는 단일 규칙표. 응답 코드 해석과 명령별 예외 규칙은 여기에만 둔다(RFC 959 응답 분류 기준).
 * 서버가 보낸 거부 응답(4xx·5xx)은 응답을 끝까지 읽은 것이므로 제어 채널이 살아 있다 — 이 항목만 실패([IoResult.ItemFailed])이고 세션은 유지한다.
 * 응답을 읽지 못한 실패(연결 닫힘·421·타임아웃)는 commons-net이 예외로 던지며, [FtpSession.withConnectedClient] 한 곳이 연결 끊김으로 분류한다.
 */
internal object FtpReplyRules {

    /**
     * 목록 조회 명령(LIST·MLSD)의 마지막 응답이 목록을 끝까지 보냈다는 뜻인지 판별한다.
     * 거부 응답(4xx·5xx)은 명령·조회 목적과 무관하게 이 조회의 실패다 — 빈 목록으로 보면 조회 실패가 빈 폴더로 보인다.
     * 지원 범위는 빈 디렉터리에 빈 목록과 226으로 답하는 서버다. RFC 959에서 450은 "file unavailable" 일시 거부이며 빈 목록이라는 뜻이 없다
     * ("450 No files found"는 NLST·글롭 조회에 일치하는 항목이 없다는 응답이다(ProFTPD). 이 앱은 디렉터리로 이동한 뒤 인자 없는 LIST만 보낸다).
     * @param replyCode 서버의 마지막 응답 코드
     * @return 목록을 끝까지 보냈으면(2xx) true
     */
    fun isListingCompleted(replyCode: Int): Boolean = FTPReply.isPositiveCompletion(replyCode)

    /**
     * SYST 응답이 서버의 시스템 종류를 알려준 것인지 판별한다.
     * RFC 959는 215를 정하지만 다른 2xx로 답하는 서버가 있어 commons-net처럼 2xx 전체를 받아들인다.
     * 거부(4xx·5xx)는 서버가 시스템 종류를 알려주지 않은 것이다.
     * @param replyCode SYST의 응답 코드
     * @return 시스템 종류를 알려줬으면 true
     */
    fun isSystemTypeReply(replyCode: Int): Boolean = FTPReply.isPositiveCompletion(replyCode)

    /**
     * 응답이 예비 응답(1xx)인지 판별한다. 예비 응답 뒤에는 같은 명령의 최종 응답이 이어지며, 목록 조회·전송에서는 데이터 연결을 열어 전송을 시작했다는 뜻이다.
     * @param replyCode 응답 코드
     * @return 예비 응답이면 true
     */
    fun isPreliminaryReply(replyCode: Int): Boolean = FTPReply.isPositivePreliminary(replyCode)

    /**
     * [command]를 서버가 거부한 마지막 응답을 이 항목만의 실패로 만든다.
     * @param command 거부된 명령
     * @param client 거부 응답을 받은 FTPClient
     * @return 서버 응답을 메시지로 담은 실패
     */
    fun refusal(command: FtpCommand, client: FTPClient): IoResult.ItemFailed =
        IoResult.ItemFailed("${command.label} 실패: ${client.replyString.trim()}${refusalNote(command)}")

    /**
     * 서버 응답만으로는 원인을 알 수 없는 거부에 덧붙일 설명.
     * RMD는 목록에서 본 하위 항목을 모두 지운 뒤에만 보내지만, 목록은 서버가 드러내는 항목뿐이라(RFC 3659 §7.1, LIST의 유닉스 계열 `.` 항목 등)
     * 서버가 숨긴 항목이 남아 거부됐을 수 있다. 클라이언트는 그런 항목을 알아낼 방법이 없고, 거부 응답은 권한 없음과 비어 있지 않음을 구분하지 않으므로 가능성으로 알린다.
     * @param command 거부된 명령
     * @return 덧붙일 설명. 없으면 빈 문자열
     */
    private fun refusalNote(command: FtpCommand): String = when (command) {
        FtpCommand.RMD -> HIDDEN_ENTRIES_NOTE
        FtpCommand.CWD, FtpCommand.LIST, FtpCommand.MLSD, FtpCommand.RENAME, FtpCommand.DELE, FtpCommand.MKD,
        FtpCommand.RETR, FtpCommand.STOR -> ""
    }

    /** 숨김 항목이 남아 RMD가 거부됐을 수 있을 때 거부 메시지 뒤에 덧붙이는 설명([refusalNote]). */
    internal const val HIDDEN_ENTRIES_NOTE = " (폴더가 비어 있지 않아 거부됐다면 목록에 보이지 않는 항목(숨김 파일 등)이 남아 있을 수 있습니다)"
}
