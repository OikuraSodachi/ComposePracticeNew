package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FtpSessionManager
import com.todokanai.composepracticenew.data.ftp.ListedItem
import com.todokanai.composepracticenew.data.ftp.ftpUriOf
import com.todokanai.composepracticenew.data.ftp.isFtpUri
import com.todokanai.composepracticenew.data.ftp.parentFtpUri
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.map
import com.todokanai.fileexplorer.defaultDisplayName
import javax.inject.Inject

/**
 * ftp:// 스킴으로 시작하는 원격 경로를 처리하는 파일 시스템 — 탐색·경로 규칙·이름 제약·이름 비교 역할을 모두 구현한다.
 * 목록은 탐색 세션에서 조회한다(#436).
 * @param sessions 목록을 조회할 탐색 세션을 가진 관리자
 */
class FtpPathAwareFileSystem @Inject internal constructor(
    private val sessions: FtpSessionManager
) : NavigableFileSystem, PathRuleFileSystem, NameRuleFileSystem, NameMatchFileSystem {
    override fun handles(path: String): Boolean = isFtpUri(path)

    // 해석하지 못한 줄은 목록에서 빼고 읽은 항목만 돌려주되, 뺀 수를 목록 결과에 담아 화면이 목록 상태로 표시하게 한다(#255, #348)
    override suspend fun listFiles(path: String): IoResult<ListedDirectory> =
        sessions.withBrowsingSession { parts -> parts.listing.listFiles(path) }.map { items ->
            ListedDirectory(
                entries = items.mapNotNull { (it as? ListedItem.Entry)?.entry },
                unreadableCount = items.count { it is ListedItem.Unreadable }
            )
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
}
