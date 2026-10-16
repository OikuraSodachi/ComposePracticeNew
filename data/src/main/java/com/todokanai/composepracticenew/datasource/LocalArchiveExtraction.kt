package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.datasource.LocalFileChecks.sumBytesOrOne
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 여러 아카이브의 압축 해제를 조율한다. 형식별 처리는 주입된 [ArchiveExtractor] 집합에 위임한다.
 * @param extractors 형식별 아카이브 추출기 집합 — 각 supports()는 서로 배타적이다
 */
class LocalArchiveExtraction @Inject constructor(
    private val extractors: Set<@JvmSuppressWildcards ArchiveExtractor>
) {

    /**
     * [zipFiles]를 [destPath]에 압축 해제한다.
     * @param zipFiles 압축 해제할 아카이브 파일 경로 집합
     * @param destPath 압축 해제 대상 디렉터리 경로
     * @param unzipHere true이면 destPath에 직접 해제, false이면 아카이브 이름의 하위 폴더 생성 후 해제
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun unzip(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        val sizes = zipFiles.map { extractorFor(it).measure(it) }
        val totalBytes = sumBytesOrOne(sizes.map { it.bytes })
        checkDiskSpace(totalBytes, File(destPath))?.let { throw Exception(it) }
        onProgress(ProgressState(progress = 0))
        val tracker = ByteProgressTracker(totalBytes, sizes.sumOf { it.fileCount }, onProgress)

        var skippedEntries = 0
        zipFiles.forEach { zipFilePath ->
            val root = if (unzipHere) File(destPath)
                       else File(destPath, File(zipFilePath).nameWithoutExtension).also { it.mkdirs() }
            skippedEntries += extractorFor(zipFilePath).extract(zipFilePath, root, tracker)
        }
        if (skippedEntries > 0) {
            throw Exception("경로 검증 실패로 ${skippedEntries}개 항목을 건너뜀")
        } else {
            onProgress(ProgressState(progress = 100))
        }
    }

    /** [path] 형식을 처리하는 추출기를 반환한다. 지원하지 않는 형식이면 예외를 던진다. */
    private fun extractorFor(path: String): ArchiveExtractor =
        extractors.firstOrNull { it.supports(path) }
            ?: throw Exception("지원하지 않는 압축 형식: ${File(path).name}")
}
