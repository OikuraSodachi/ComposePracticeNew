package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpListingFormat
import com.todokanai.composepracticenew.data.ftp.ListedItem
import com.todokanai.composepracticenew.data.ftp.extractFtpPath
import com.todokanai.composepracticenew.data.ftp.ftpUriOf
import com.todokanai.composepracticenew.data.ftp.isFtpUri
import com.todokanai.composepracticenew.data.ftp.parentFtpUri
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.map
import com.todokanai.fileexplorer.defaultDisplayName
import javax.inject.Inject

/**
 * ftp:// 스킴으로 시작하는 원격 경로를 처리하는 PathAwareFileSystem 구현체.
 * @param ftpCommands FTP 디렉터리 목록 조회를 담당하는 FtpCommandExecutor
 * @param notices 목록에서 해석하지 못한 줄을 알리는 OperationNoticeReporter — 목록 결과는 항목만 담으므로 직접 알린다
 */
class FtpPathAwareFileSystem @Inject constructor(
    private val ftpCommands: FtpCommandExecutor,
    private val notices: OperationNoticeReporter
) : PathAwareFileSystem {
    override fun handles(path: String): Boolean = isFtpUri(path)

    // 해석하지 못한 줄은 목록에서 빼고 읽은 항목만 보여 주되, 빠진 줄이 있음을 알리고 불완전한 목록으로 돌려준다(#255)
    override suspend fun listFiles(path: String): IoResult<ListedDirectory> =
        ftpCommands.listFiles(path).map { items ->
            val unreadable = items.filterIsInstance<ListedItem.Unreadable>()
            if (unreadable.isNotEmpty()) notices.report(OperationNoticeKind.FAILURE, unreadableNotice(path, unreadable))
            ListedDirectory(items.mapNotNull { (it as? ListedItem.Entry)?.entry }, isComplete = unreadable.isEmpty())
        }

    override fun getParent(path: String): String? = parentFtpUri(path)
    // 루트(ftp://host)는 호스트 이름을 표시한다
    override fun displayName(path: String): String = defaultDisplayName(path)
    // 서버의 이름 제약은 알 수 없어(RFC 959·3659에 이름 제약을 묻는 명령이 없다) 주요 파일시스템의 길이 한도만 적용하고, 그 밖의 거부는 서버 응답으로 알린다
    override fun nameViolationsOf(fullName: String, location: String): Set<FileNameError> = nameByteLimitViolations(fullName)
    // 서버가 대소문자·정규형을 구분하는지는 알 수 없어(RFC 959·2389·3659에 묻는 명령이 없다) 어느 서버에서든 같은 항목이 될 수 있는 이름을 묶는다
    override fun nameMatchKeyOf(name: String, location: String): String = caseAndNormalizationInsensitiveKey(name)
    // 원격 경로는 네트워크 I/O 없이 사전 접근성 검사를 할 수 없으므로 정규화만 수행하고 항상 non-null 반환
    override suspend fun resolveNavigationPath(path: String): String = ftpUriOf(path)

    /**
     * 목록에서 해석하지 못한 줄을 알리는 문구를 만든다. 첫 줄만 보여 주고 나머지는 개수로 줄인다.
     * @param path 조회한 디렉터리 경로 (ftp://server/path 형식)
     * @param unreadable 해석하지 못한 줄
     * @return 표시할 문구
     */
    private fun unreadableNotice(path: String, unreadable: List<ListedItem.Unreadable>): String {
        val more = if (unreadable.size > 1) " 외 ${unreadable.size - 1}개" else ""
        return "${extractFtpPath(path)}: ${FtpListingFormat.unreadableLineReason(unreadable.first().line)}$more — 읽은 항목만 표시합니다"
    }
}
