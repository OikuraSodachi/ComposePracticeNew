package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.ftpUriOf
import com.todokanai.composepracticenew.data.ftp.isFtpUri
import com.todokanai.composepracticenew.data.ftp.parentFtpUri
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.defaultDisplayName
import javax.inject.Inject

/**
 * ftp:// 스킴으로 시작하는 원격 경로를 처리하는 PathAwareFileSystem 구현체.
 * @param ftpCommands FTP 디렉터리 목록 조회를 담당하는 FtpCommandExecutor
 */
class FtpPathAwareFileSystem @Inject constructor(
    private val ftpCommands: FtpCommandExecutor
) : PathAwareFileSystem {
    override fun handles(path: String): Boolean = isFtpUri(path)
    override suspend fun listFiles(path: String): IoResult<List<FileEntry>> = ftpCommands.listFiles(path)
    override fun getParent(path: String): String? = parentFtpUri(path)
    // 루트(ftp://host)는 호스트 이름을 표시한다
    override fun displayName(path: String): String = defaultDisplayName(path)
    // 서버의 이름 제약은 알 수 없어(RFC 959·3659에 이름 제약을 묻는 명령이 없다) 주요 파일시스템의 길이 한도만 적용하고, 그 밖의 거부는 서버 응답으로 알린다
    override fun nameViolationsOf(fullName: String, location: String): Set<FileNameError> = nameByteLimitViolations(fullName)
    // 원격 경로는 네트워크 I/O 없이 사전 접근성 검사를 할 수 없으므로 정규화만 수행하고 항상 non-null 반환
    override suspend fun resolveNavigationPath(path: String): String = ftpUriOf(path)
}
