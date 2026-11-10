package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.TransferTotal
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileNameParts
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
     * @param layout 압축 해제한 항목을 [destPath]의 어디에 둘지 정하는 배치 방식
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 압축 해제 결과. 지원하지 않는 형식이 있으면 아무것도 해제하지 않고 실패한다
     */
    suspend fun unzip(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo {
            val archives = zipFiles.map { path ->
                path to (extractorFor(path) ?: return@localIo IoResult.ItemFailed("지원하지 않는 압축 형식: ${File(path).name}"))
            }
            val sizes = archives.map { (path, extractor) -> extractor.measure(path) }
            val knownBytes = sizes.sumOf { it.knownBytes }
            // 크기를 모르는 항목은 공간 검사에 넣지 못한다 — 그 몫이 부족하면 쓰기 단계의 실패로 끝난다
            checkDiskSpace(knownBytes, File(destPath).freeSpace)?.let { return@localIo IoResult.ItemFailed(it) }
            val total = TransferTotal.of(knownBytes, sizes.sumOf { it.unknownSizeCount })
            val tracker = ByteProgressTracker(total, sizes.sumOf { it.fileCount }, onProgress)
            tracker.reportStart()

            var skippedEntries = 0
            archives.forEach { (zipFilePath, extractor) ->
                val root = rootOf(File(destPath), zipFilePath, layout).also(::createDirectories)
                val writer = ArchiveEntryWriter(ExtractionRoot(root))
                extractor.extract(zipFilePath, writer, tracker)
                skippedEntries += writer.skippedEntries
            }
            if (skippedEntries > 0) return@localIo IoResult.ItemFailed("경로 검증 실패로 ${skippedEntries}개 항목을 건너뜀")
            tracker.reportComplete()
            IoResult.Done
        }
    }

    /**
     * [zipFile]을 [destPath]에 [layout]으로 압축 해제할 때 [destPath] 바로 아래에 생기는(또는 덮어쓰는) 이름을 구한다.
     * [unzip]과 같은 [rootOf]·항목 위치 규칙으로 구하므로, 이 이름으로 충돌을 판정하면 해제가 [destPath]에서 건드리는 이름을 빠뜨리지 않는다.
     * @param zipFile 아카이브 파일 경로
     * @param destPath 압축 해제 대상 디렉터리 경로
     * @param layout 압축 해제한 항목을 [destPath]의 어디에 둘지 정하는 배치 방식
     * @return [destPath] 바로 아래 이름 집합, 또는 지원하지 않는 형식이거나 아카이브를 읽지 못한 실패
     */
    suspend fun extractedNames(zipFile: String, destPath: String, layout: ExtractLayout): IoResult<Set<String>> = withContext(Dispatchers.IO) {
        localIo {
            val extractor = extractorFor(zipFile) ?: return@localIo IoResult.ItemFailed("지원하지 않는 압축 형식: ${File(zipFile).name}")
            val dest = File(destPath)
            val names = when (layout) {
                ExtractLayout.IntoArchiveFolder -> setOf(rootOf(dest, zipFile, layout).name)
                ExtractLayout.IntoDestination -> {
                    val root = ExtractionRoot(dest)
                    extractor.entryNames(zipFile).mapNotNullTo(HashSet()) { root.topLevelNameOf(it) }
                }
            }
            IoResult.Success(names)
        }
    }

    /**
     * [layout]에 따라 [zipFile]의 항목을 풀 루트 디렉터리를 정한다.
     * @param dest 압축 해제 대상 디렉터리
     * @param zipFile 아카이브 파일 경로
     * @param layout 배치 방식
     * @return 아카이브 폴더에 풀면 [dest] 아래 아카이브 이름(확장자 제외)의 폴더, 바로 풀면 [dest]
     */
    private fun rootOf(dest: File, zipFile: String, layout: ExtractLayout): File = when (layout) {
        ExtractLayout.IntoArchiveFolder -> File(dest, archiveFolderName(File(zipFile).name))
        ExtractLayout.IntoDestination -> dest
    }

    /**
     * 아카이브 폴더의 이름을 정한다. 숨김 표시인 첫 점은 확장자로 나누지 않으므로(예: `.zip`) 이름이 비어 대상 디렉터리에 바로 풀리는 일이 없다.
     * @param archiveName 아카이브 파일 이름
     * @return 확장자를 뺀 이름
     */
    private fun archiveFolderName(archiveName: String): String = archiveName.substring(0, FileNameParts.baseNameEnd(archiveName))

    /**
     * [path]를 압축 해제할 수 있는지 판별한다. [unzip]이 쓰는 추출기 집합으로만 정하므로 실제 지원 형식과 어긋나지 않는다.
     * @param path 아카이브 파일 경로
     * @return 처리할 추출기가 있으면 true
     */
    fun canExtract(path: String): Boolean = extractorFor(path) != null

    /**
     * [path] 형식을 처리하는 추출기를 찾는다.
     * @param path 아카이브 파일 경로
     * @return 추출기. 지원하지 않는 형식이면 null
     */
    private fun extractorFor(path: String): ArchiveExtractor? = extractors.firstOrNull { it.supports(path) }
}
