package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.repository.OperationNoticeReporter

/**
 * 한 세션 위에 FTP 명령 부품(목록 받기·명령 실행·화면 명령·트리 수집·삭제·전송)을 조립한다. 부품은 모두 이 세션에만 명령을 보낸다.
 * 세션은 접속 한 번만 살므로([FtpSession]), 세션마다 새로 조립한다.
 * @param session 모든 부품이 쓰는 FTP 세션
 * @param treeMemoryLimitBytes 트리 수집의 메모리 상한(바이트). null이면 앱의 기본 상한
 */
internal class FtpCommandParts<S>(private val session: S, treeMemoryLimitBytes: Long? = null) where S : FtpCommandSession, S : FtpDataCommandSession {

    /** 목록을 받아 해석하는 부품. */
    val listing = FtpListingReader(session, DataCommandRunner(session))

    /** 성공 여부를 반환하는 명령과 대상 밖 이동을 실행하는 부품. */
    val runner = FtpCommandRunner(session)

    /** 이름 변경·폴더 생성·폴더 확인을 실행하는 부품. */
    val executor = FtpCommandExecutor(session, runner)

    /** 원격 트리를 모으는 부품. */
    val trees = treeMemoryLimitBytes?.let { FtpTreeCollector(listing, it) } ?: FtpTreeCollector(listing)

    /** 고른 원격 항목을 지우는 부품. */
    val deletion = FtpRemoteDeletion(trees, runner)

    /**
     * 이 부품들로 전송기를 만든다.
     * @param notices 취소한 업로드의 정리 실패를 받을 알림함
     * @return 이 부품들의 화면 명령·트리 수집을 쓰는 전송기
     */
    fun transferrer(notices: OperationNoticeReporter): FtpTransferrer =
        FtpTransferrer(session, executor, trees, DataCommandRunner(session), notices)
}
