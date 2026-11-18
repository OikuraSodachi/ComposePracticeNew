package com.todokanai.composepracticenew.data.ftp

/**
 * 탐색 세션 하나 위에 목록 조회·진행 표시가 없는 원격 작업(이름 변경·새 폴더)의 FTP 명령 부품을 조립한다. 부품은 모두 이 세션에만 명령을 보낸다.
 * 삭제·전송 부품은 담지 않는다 — 진행 표시가 있는 작업은 작업 세션([FtpOperationParts])에서만 실행되도록(#441, `.claude/rules/ftp.md`의 세션 단위).
 * 세션은 접속 한 번만 살므로([FtpSession]), 세션마다 새로 조립한다.
 * @param session 모든 부품이 쓰는 FTP 세션
 */
internal class FtpBrowsingParts<S>(session: S) where S : FtpCommandSession, S : FtpDataCommandSession {

    /** 목록을 받아 해석하는 부품. */
    val listing = FtpListingReader(session, DataCommandRunner(session))

    /** 이름 변경·폴더 생성·폴더 확인을 실행하는 부품. */
    val executor = FtpCommandExecutor(session, FtpCommandRunner(session))
}
