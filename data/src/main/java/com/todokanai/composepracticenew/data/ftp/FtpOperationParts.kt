package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.repository.OperationNoticeReporter

/**
 * 작업 세션 하나 위에 진행 표시가 있는 원격 작업(삭제·전송)의 FTP 명령 부품을 조립한다. 부품은 모두 이 세션에만 명령을 보낸다.
 * 탐색 세션은 [FtpBrowsingParts]만 받으므로 삭제·전송 부품을 쓸 수 없다(#441, `.claude/rules/ftp.md`의 세션 단위).
 * 세션은 접속 한 번만 살므로([FtpSession]), 세션마다 새로 조립한다.
 * @param session 모든 부품이 쓰는 FTP 세션
 * @param treeMemoryLimitBytes 트리 수집의 메모리 상한(바이트). null이면 앱의 기본 상한
 */
internal class FtpOperationParts<S>(private val session: S, treeMemoryLimitBytes: Long? = null) where S : FtpCommandSession, S : FtpDataCommandSession {

    /** 목록을 받아 해석하는 부품. 트리 수집이 쓴다. */
    val listing = FtpListingReader(session, DataCommandRunner(session))

    /** 성공 여부를 반환하는 명령과 대상 밖 이동을 실행하는 부품. */
    private val runner = FtpCommandRunner(session)

    /** 이름 변경·폴더 생성·폴더 확인을 실행하는 부품. 업로드가 쓴다. */
    val executor = FtpCommandExecutor(session, runner)

    /** 원격 트리를 모으는 부품. */
    val trees = treeMemoryLimitBytes?.let { FtpTreeCollector(listing, it) } ?: FtpTreeCollector(listing)

    /** 고른 원격 항목을 지우는 부품. */
    val deletion = FtpRemoteDeletion(trees, runner)

    /** 원격 항목을 로컬로 받는 부품. */
    val downloader = FtpDownloader(session, trees, DataCommandRunner(session))

    /**
     * 이 부품들로 업로드 부품을 만든다.
     * @param notices 취소한 업로드의 정리 실패를 받을 알림함
     * @return 이 부품들의 폴더 생성을 쓰는 업로드 부품
     */
    fun uploader(notices: OperationNoticeReporter): FtpUploader =
        FtpUploader(session, executor, DataCommandRunner(session), notices)
}
