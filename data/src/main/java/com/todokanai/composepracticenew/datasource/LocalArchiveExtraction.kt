package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.datasource.LocalFileChecks.sumBytesOrOne
import com.todokanai.composepracticenew.model.IoResult
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
     * @return 압축 해제 결과. 지원하지 않는 형식이 있으면 아무것도 해제하지 않고 실패한다
     */
    suspend fun unzip(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo {
            val archives = zipFiles.map { path ->
                path to (extractorFor(path) ?: return@localIo IoResult.ItemFailed("지원하지 않는 압축 형식: ${File(path).name}"))
            }
            val sizes = archives.map { (path, extractor) -> extractor.measure(path) }
            val totalBytes = sumBytesOrOne(sizes.map { it.bytes })
            checkDiskSpace(totalBytes, File(destPath))?.let { return@localIo IoResult.ItemFailed(it) }
            onProgress(ProgressState(progress = 0))
            val tracker = ByteProgressTracker(totalBytes, sizes.sumOf { it.fileCount }, onProgress)

            var skippedEntries = 0
            archives.forEach { (zipFilePath, extractor) ->
                val root = if (unzipHere) File(destPath)
                           else File(destPath, File(zipFilePath).nameWithoutExtension).also { it.mkdirs() }
                skippedEntries += extractor.extract(zipFilePath, root, tracker)
            }
            if (skippedEntries > 0) return@localIo IoResult.ItemFailed("경로 검증 실패로 ${skippedEntries}개 항목을 건너뜀")
            onProgress(ProgressState(progress = 100))
            IoResult.Done
        }
    }

    /**
     * [path] 형식을 처리하는 추출기를 찾는다.
     * @param path 아카이브 파일 경로
     * @return 추출기. 지원하지 않는 형식이면 null
     */
    private fun extractorFor(path: String): ArchiveExtractor? = extractors.firstOrNull { it.supports(path) }
}
