package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.ArchiveExtractor
import com.todokanai.composepracticenew.datasource.ArchiveNameRole
import com.todokanai.composepracticenew.datasource.archiveTreeOf
import com.todokanai.composepracticenew.datasource.askingPasswordSession
import com.todokanai.composepracticenew.datasource.localIo
import com.todokanai.composepracticenew.datasource.matchOf
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 로컬 압축 파일의 항목 목록을 형식별 [ArchiveExtractor]로 읽어 폴더 트리로 만드는 ArchiveContentRepository.
 * 형식은 압축 해제와 같은 이름 규칙으로 고르며, 분할 압축은 시작 볼륨만 받는다.
 * @param extractors 형식별 아카이브 추출기 집합(압축 해제와 같은 것)
 */
class ArchiveContentRepositoryImpl @Inject constructor(
    private val extractors: Set<@JvmSuppressWildcards ArchiveExtractor>
) : ArchiveContentRepository {

    override fun canView(path: String): Boolean = extractors.matchOf(File(path).name)?.role is ArchiveNameRole.Entry

    override suspend fun contentsOf(archivePath: String, askPassword: ArchivePasswordProvider): IoResult<ArchiveFolder> = withContext(Dispatchers.IO) {
        val name = File(archivePath).name
        // 추출 라이브러리의 콜백은 일시 중단할 수 없으므로 그 자리에서 답을 기다린다. 읽는 코루틴의 Job을 부모로 두어 화면을 나가면 기다림도 끝난다
        val job = coroutineContext.job
        localIo {
            val match = extractors.matchOf(name)?.takeIf { it.role is ArchiveNameRole.Entry }
                ?: return@localIo IoResult.ItemFailed(FailureReason.UnsupportedArchiveFormat(name))
            // 목록 읽기는 내용을 풀지 않아 비밀번호 확인 단계가 없다. 보고 자리에서는 취소만 확인한다
            val passwords = askingPasswordSession(name, askPassword, job) { job.ensureActive() }
            val entries = match.extractor.entries(archivePath, passwords)
            job.ensureActive()
            IoResult.Success(archiveTreeOf(entries))
        }
    }
}
