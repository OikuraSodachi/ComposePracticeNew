package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.TransferTotal
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
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
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로. 폴더끼리는 묻지 않고 합친다
     * @param askPassword 암호화된 아카이브의 비밀번호를 묻는 통로. 아카이브마다 필요할 때 따로 묻는다
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 압축 해제 결과. 지원하지 않는 형식이 있으면 아무것도 해제하지 않고 실패한다. 루트 밖을 가리키거나(경로 검증 실패) 자리에 심볼릭 링크가 있어 풀지 않은 항목이 있으면, 그 항목들을 담은 [IoResult.PartiallyFailed]
     */
    suspend fun unzip(
        zipFiles: Set<String>,
        destPath: String,
        layout: ExtractLayout,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> = withContext(Dispatchers.IO) {
        // 추출 라이브러리의 콜백은 일시 중단할 수 없으므로 그 자리에서 답을 기다린다. 이 작업의 Job을 부모로 두어 작업 취소가 기다림을 끝낸다
        val job = coroutineContext.job
        val ask: (WriteConflict) -> ConflictChoice = { conflict -> runBlocking(job) { resolveConflict.resolve(conflict) } }
        localIo {
            val archives = zipFiles.map { path ->
                path to (extractorFor(path) ?: return@localIo IoResult.ItemFailed(FailureReason.UnsupportedArchiveFormat(File(path).name)))
            }
            // 아카이브마다 비밀번호 세션을 따로 둔다 — 한 아카이브에서 확인한 비밀번호를 다른 아카이브에 쓰지 않도록. 산정과 추출은 같은 세션을 쓴다
            val sessions = archives.map { (path, _) -> passwordSessionOf(File(path).name, askPassword, job) }
            val sizes = archives.mapIndexed { i, (path, extractor) -> extractor.measure(path, sessions[i]) }
            val knownBytes = sizes.sumOf { it.knownBytes }
            // 크기를 모르는 항목은 공간 검사에 넣지 못한다 — 그 몫이 부족하면 쓰기 단계의 실패로 끝난다
            checkDiskSpace(knownBytes, File(destPath).freeSpace)?.let { return@localIo IoResult.ItemFailed(it) }
            val total = TransferTotal.of(knownBytes, sizes.sumOf { it.unknownSizeCount })
            val tracker = ByteProgressTracker(total, sizes.sumOf { it.fileCount }, onProgress)
            tracker.reportStart()

            val failures = mutableListOf<FailedItem>()
            archives.forEachIndexed { i, (zipFilePath, extractor) ->
                val root = rootOf(File(destPath), zipFilePath, layout)
                // 바로 풀기의 루트는 사용자가 고른 목적지 폴더라 확인하지 않는다(링크로 들어온 폴더일 수 있다). 아카이브 폴더는 새로 놓는 자리라 확인한다
                val slot = if (layout == ExtractLayout.IntoDestination) WriteSlot.Ready(Unit) else mergeDirectoryResolving(root, ask)
                // 아카이브 폴더 자리에 파일이나 링크가 있어 건너뛰면 그 아카이브의 항목을 모두 처리한 것으로 넘긴다
                if (slot !is WriteSlot.Ready) {
                    if (slot == WriteSlot.Link) failures += FailedItem(root.name, FailureReason.UnsupportedLink())
                    tracker.skipFiles(sizes[i].fileCount)
                    tracker.addBytes(sizes[i].knownBytes, root.name, fileBytes = null)
                    return@forEachIndexed
                }
                val writer = ArchiveEntryWriter(ExtractionRoot(root), ask, tracker)
                extractor.extract(zipFilePath, writer, sessions[i], tracker)
                failures += writer.failures.map { if (layout == ExtractLayout.IntoDestination) it else it.under(root.name) }
            }
            tracker.reportComplete()
            if (failures.isEmpty()) IoResult.Done else IoResult.PartiallyFailed(failures)
        }
    }

    /**
     * 아카이브 하나의 비밀번호 세션을 만든다. 추출 라이브러리의 콜백 안에서도 묻도록, 일시 중단하지 않고 [job]을 부모로 답을 기다린다 — 작업 취소가 기다림을 끝낸다.
     * @param archiveName 비밀번호를 물을 때 보일 아카이브 이름
     * @param askPassword 비밀번호를 묻는 통로
     * @param job 이 작업의 Job
     * @return 새 비밀번호 세션
     */
    private fun passwordSessionOf(archiveName: String, askPassword: ArchivePasswordProvider, job: Job) = ArchivePasswordSession(
        ask = { prompt -> runBlocking(job) { askPassword.passwordFor(archiveName, prompt) } },
        ensureActive = { job.ensureActive() },
    )

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
