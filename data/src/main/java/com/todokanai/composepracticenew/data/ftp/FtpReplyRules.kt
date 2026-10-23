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

/** 서버가 정상적으로 보낸(제어 채널이 살아 있는) 목록 조회 응답의 의미. */
internal enum class FtpListingReply {
    /** 2xx — 조회한 항목이 목록이다. */
    COMPLETED,
    /** 빈 디렉터리일 수 있는 거부 응답 — 실제로 빈 디렉터리인지는 알 수 없으므로 확인되지 않은 빈 목록이다. */
    NO_ENTRIES,
    /** 서버가 이 조회만 거부했다(4xx 일시 거부·5xx 영구 거부). */
    REFUSED,
}

/** 목록을 조회하는 목적. 같은 응답이라도 잘못 해석했을 때의 결과가 달라 해석 규칙이 갈린다. */
internal enum class FtpListingPurpose {
    /** 화면에 보여줄 목록. 빈 디렉터리로 볼 응답은 확인되지 않은 빈 목록으로 표시하고, 이름 충돌 판정은 그 목록을 믿지 않고 사용자에게 확인받는다. */
    BROWSE,
    /** 작업(다운로드·삭제)할 하위 트리 수집. 빈 목록으로 잘못 보면 그 폴더의 항목이 작업에서 빠진 채 성공으로 끝난다. */
    TREE,
}

/**
 * FTP 서버 응답을 결과로 해석하는 단일 규칙표. 응답 코드 해석과 명령별 예외 규칙은 여기에만 둔다(RFC 959 응답 분류 기준).
 * 서버가 보낸 거부 응답(4xx·5xx)은 응답을 끝까지 읽은 것이므로 제어 채널이 살아 있다 — 이 항목만 실패([IoResult.ItemFailed])이고 세션은 유지한다.
 * 응답을 읽지 못한 실패(연결 닫힘·421·타임아웃)는 commons-net이 예외로 던지며, [FtpSession.withConnectedClient] 한 곳이 연결 끊김으로 분류한다.
 */
internal object FtpReplyRules {

    /**
     * 목록 조회 명령의 마지막 응답을 해석한다.
     * 화면 목록에서 LIST가 데이터 연결을 열기 전에 받은 450은 빈 목록이다 — 빈 디렉터리에 "450 No files found"를 보내는 서버가 있다(curl과 같은 규칙).
     * 450은 "file busy" 같은 일시 거부이기도 하므로, 빈 목록으로 잘못 보면 항목이 빠진 채 성공하는 트리 수집에서는 거부로 둔다.
     * 데이터 연결을 열어(1xx) 목록을 보내기 시작한 뒤의 450은 전송이 중단된 것이므로 언제나 거부다.
     * 550은 없는 경로·권한 없음과 구분할 수 없으므로 거부로 둔다. 빈 목록으로 보면 조회 실패가 빈 폴더로 보인다.
     * MLSD는 빈 디렉터리에 빈 목록과 226을 보내도록 정해져 있어(RFC 3659) 예외 규칙이 없다.
     * @param command 목록 조회 명령
     * @param purpose 조회 목적
     * @param replyCode 서버의 마지막 응답 코드
     * @param dataTransferStarted 서버가 예비 응답(1xx)을 보내 목록 전송을 시작했는지 여부
     * @return 응답의 의미
     */
    fun listingReplyOf(command: FtpCommand, purpose: FtpListingPurpose, replyCode: Int, dataTransferStarted: Boolean): FtpListingReply =
        when {
            FTPReply.isPositiveCompletion(replyCode) -> FtpListingReply.COMPLETED
            isEmptyDirectoryReply(command, purpose, replyCode, dataTransferStarted) -> FtpListingReply.NO_ENTRIES
            else -> FtpListingReply.REFUSED
        }

    /**
     * 거부 응답이 빈 디렉터리를 뜻하는지 판별한다. 규칙은 [listingReplyOf]에 적었다.
     * @param command 목록 조회 명령
     * @param purpose 조회 목적
     * @param replyCode 서버의 마지막 응답 코드
     * @param dataTransferStarted 서버가 목록 전송을 시작했는지 여부
     * @return 빈 목록으로 볼 응답이면 true
     */
    private fun isEmptyDirectoryReply(command: FtpCommand, purpose: FtpListingPurpose, replyCode: Int, dataTransferStarted: Boolean): Boolean =
        command == FtpCommand.LIST && purpose == FtpListingPurpose.BROWSE &&
            replyCode == FTPReply.FILE_ACTION_NOT_TAKEN && !dataTransferStarted

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
     * 연결할 때 `-a` 옵션을 쓸지 정하려고 보낸 LIST가 목록을 돌려줬는지 판별한다([FtpListingFormat.probeHiddenListing]).
     * 화면 목록과 달리 450을 빈 목록으로 보지 않는다 — `-a`를 경로로 읽은 서버가 없는 경로를 450으로 답하면 빈 목록으로 보고 옵션을 쓰게 된다.
     * @param replyCode LIST의 마지막 응답 코드
     * @return 목록을 돌려줬으면 true
     */
    fun isListingProbeCompleted(replyCode: Int): Boolean = FTPReply.isPositiveCompletion(replyCode)

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
     * RMD는 목록에서 본 하위 항목을 모두 지운 뒤에만 보내지만, 목록은 서버가 보여주려는 항목만 담으므로(RFC 3659 §7.1)
     * 숨김 항목이 남아 거부될 수 있다. 클라이언트는 그런 항목을 알아낼 방법이 없어 가능성을 알린다.
     * @param command 거부된 명령
     * @return 덧붙일 설명. 없으면 빈 문자열
     */
    private fun refusalNote(command: FtpCommand): String = when (command) {
        FtpCommand.RMD -> " (목록에 보이지 않는 항목(숨김 파일 등)이 남아 있으면 지울 수 없습니다)"
        FtpCommand.CWD, FtpCommand.LIST, FtpCommand.MLSD, FtpCommand.RENAME, FtpCommand.DELE, FtpCommand.MKD,
        FtpCommand.RETR, FtpCommand.STOR -> ""
    }
}
