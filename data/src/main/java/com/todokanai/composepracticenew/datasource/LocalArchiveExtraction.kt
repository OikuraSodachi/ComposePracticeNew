package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.IndeterminateProgressReporter
import com.todokanai.composepracticenew.data.progress.TransferTotal
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * 아카이브 하나(단일 아카이브 또는 분할 압축 한 세트)의 압축 해제를 조율한다. 형식별 처리는 주입된 [ArchiveExtractor] 집합에 위임한다.
 * @param extractors 형식별 아카이브 추출기 집합 — 각 nameRoleOf()가 역할을 주는 이름은 서로 배타적이다
 */
class LocalArchiveExtraction @Inject constructor(
    private val extractors: Set<@JvmSuppressWildcards ArchiveExtractor>
) {

    /**
     * [zipFiles]를 [destPath]에 압축 해제한다.
     * @param zipFiles 압축 해제할 아카이브 파일 경로 집합. 아카이브 하나로 묶여야 한다([extractionTargetOf]가 null이 아니어야 한다) — 단일 아카이브 하나, 또는 분할 압축의 시작 볼륨과 그 세트의 다른 볼륨들
     * @param destPath 압축 해제 대상 디렉터리 경로
     * @param layout 압축 해제한 항목을 [destPath]의 어디에 둘지 정하는 배치 방식
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로. 폴더끼리는 묻지 않고 합친다
     * @param askPassword 암호화된 아카이브의 비밀번호를 묻는 통로. 아카이브마다 필요할 때 따로 묻는다
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 압축 해제 결과. 풀 수 없는 경로(지원하지 않는 형식, 시작 볼륨 없이 받은 분할 압축의 다른 볼륨)가 있으면 아무것도 해제하지 않고 실패한다.
     * 분할 압축의 볼륨이 빠졌으면 알아챈 때에 [FailureReason.MissingArchiveVolume]으로 실패한다(이미 만든 항목은 남는다).
     * 루트 밖을 가리키거나(경로 검증 실패) 자리에 심볼릭 링크가 있어 풀지 않은 항목이 있으면, 그 항목들을 담은 [IoResult.PartiallyFailed]
     * @throws IllegalArgumentException 풀 수 있는 경로지만 아카이브 하나로 묶이지 않을 때(없거나 둘 이상) — 아무것도 풀지 않는다
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
        localIo(::extractionFailureReasonOf) {
            val archive = when (val grouping = groupIntoArchives(zipFiles)) {
                is ArchiveGrouping.Archive -> grouping.archive
                is ArchiveGrouping.NotOneArchive -> throw IllegalArgumentException("아카이브 하나로 묶이는 경로만 받는다: 아카이브 ${grouping.count}개")
                is ArchiveGrouping.Unextractable -> return@localIo IoResult.ItemFailed(FailureReason.UnsupportedArchiveFormat(File(grouping.path).name))
            }
            // 산정과 추출은 같은 비밀번호 세션을 쓴다 — 산정에서 확인한 비밀번호를 추출에서 다시 묻지 않도록
            val passwords = passwordSessionOf(File(archive.path).name, askPassword, job, IndeterminateProgressReporter(onProgress))
            val size = archive.extractor.measure(archive.path, passwords)
            // 크기를 모르는 항목은 공간 검사에 넣지 못한다 — 그 몫이 부족하면 쓰기 단계의 실패로 끝난다
            checkDiskSpace(size.knownBytes, File(destPath).freeSpace)?.let { return@localIo IoResult.ItemFailed(it) }
            val tracker = ByteProgressTracker(TransferTotal.of(size.knownBytes, size.unknownSizeCount), size.fileCount, onProgress)
            tracker.reportStart()

            val root = rootOf(File(destPath), archive, layout)
            // 바로 풀기의 루트는 사용자가 고른 목적지 폴더라 확인하지 않는다(링크로 들어온 폴더일 수 있다). 아카이브 폴더는 새로 놓는 자리라 확인한다
            val slot = if (layout == ExtractLayout.IntoDestination) WriteSlot.Ready(Unit) else mergeDirectoryResolving(root, ask)
            // 아카이브 폴더 자리에 파일이나 링크가 있어 건너뛰면 아무것도 풀지 않고 끝낸다
            if (slot !is WriteSlot.Ready) {
                tracker.reportComplete()
                return@localIo if (slot == WriteSlot.Link) IoResult.PartiallyFailed(listOf(FailedItem(root.name, FailureReason.UnsupportedLink()))) else IoResult.Done
            }
            val writer = ArchiveEntryWriter(ExtractionRoot(root), ask, tracker)
            archive.extractor.extract(archive.path, writer, passwords, tracker)
            tracker.reportComplete()
            val failures = writer.failures.map { if (layout == ExtractLayout.IntoDestination) it else it.under(root.name) }
            if (failures.isEmpty()) IoResult.Done else IoResult.PartiallyFailed(failures)
        }
    }

    /**
     * 아카이브 하나의 비밀번호 세션을 만든다. 추출 라이브러리의 콜백 안에서도 묻도록, 일시 중단하지 않고 [job]을 부모로 답을 기다린다 — 작업 취소가 기다림을 끝낸다.
     * @param archiveName 비밀번호를 물을 때 보일 아카이브 이름
     * @param askPassword 비밀번호를 묻는 통로
     * @param job 이 작업의 Job
     * @param checking 비밀번호 확인 단계를 보고하는 보고기. 그 보고가 확인하는 동안의 작업 취소를 확인한다
     * @return 새 비밀번호 세션
     */
    private fun passwordSessionOf(archiveName: String, askPassword: ArchivePasswordProvider, job: Job, checking: IndeterminateProgressReporter) = ArchivePasswordSession(
        ask = { prompt -> runBlocking(job) { askPassword.passwordFor(archiveName, prompt) } },
        reportChecking = { checking.report { IndeterminatePhase.CheckingPassword(archiveName) } },
    )

    /**
     * [layout]에 따라 [archive]의 항목을 풀 루트 디렉터리를 정한다.
     * @param dest 압축 해제 대상 디렉터리
     * @param archive 풀 아카이브
     * @param layout 배치 방식
     * @return 아카이브 폴더에 풀면 [dest] 아래 아카이브 폴더 이름(볼륨 번호와 확장자 제외)의 폴더, 바로 풀면 [dest]
     */
    private fun rootOf(dest: File, archive: TargetArchive, layout: ExtractLayout): File = when (layout) {
        ExtractLayout.IntoArchiveFolder -> File(dest, archive.folderName)
        ExtractLayout.IntoDestination -> dest
    }

    /**
     * [paths]가 [unzip]이 푸는 아카이브 하나로 묶이면 풀 때 여는 경로를 돌려준다. [unzip]과 같은 묶기 규칙을 쓰므로 실제 압축 해제와 어긋나지 않는다.
     * @param paths 압축 해제할 파일 경로들
     * @return 아카이브의 경로(단일 아카이브 또는 분할 압축의 시작 볼륨). 풀 수 없는 경로가 있거나 아카이브 하나로 묶이지 않으면 null
     */
    fun extractionTargetOf(paths: Set<String>): String? =
        (groupIntoArchives(paths) as? ArchiveGrouping.Archive)?.archive?.path

    /**
     * [paths]를 풀 아카이브로 묶는다. 분할 압축의 다른 볼륨은 같은 폴더·같은 세트의 시작 볼륨이 [paths]에 있으면 그 시작 볼륨에 합친다.
     * @param paths 압축 해제할 파일 경로들
     * @return 묶인 아카이브 하나, 아카이브 하나로 묶이지 않으면 그 수. 지원하지 않는 형식이거나 시작 볼륨 없이 받은 다른 볼륨이 있으면 그 경로
     */
    private fun groupIntoArchives(paths: Collection<String>): ArchiveGrouping {
        val named = paths.map { path -> namedPathOf(path) ?: return ArchiveGrouping.Unextractable(path) }
        val entrySets = named.filter { it.role is ArchiveNameRole.Entry }.mapTo(HashSet()) { it.setKey }
        named.firstOrNull { it.role is ArchiveNameRole.LaterVolume && it.setKey !in entrySets }?.let { return ArchiveGrouping.Unextractable(it.path) }
        val archives = named.filter { it.role is ArchiveNameRole.Entry }.map { TargetArchive(it.path, it.extractor, it.role.set.folderName) }
        return archives.singleOrNull()?.let(ArchiveGrouping::Archive) ?: ArchiveGrouping.NotOneArchive(archives.size)
    }

    /**
     * [path]의 파일 이름에 역할을 주는 추출기를 찾는다.
     * @param path 파일 경로
     * @return 추출기와 역할. 지원하지 않는 형식이면 null
     */
    private fun namedPathOf(path: String): NamedPath? {
        val name = File(path).name
        for (extractor in extractors) extractor.nameRoleOf(name)?.let { return NamedPath(path, extractor, it) }
        return null
    }

    /**
     * 압축 해제의 입출력 실패를 원인으로 바꾼다. 빠진 볼륨은 그 원인으로, 나머지는 [ioFailureReasonOf]로 바꾼다.
     * @param e 입출력 실패
     * @return 실패 원인
     */
    private fun extractionFailureReasonOf(e: IOException): FailureReason? =
        if (e is MissingArchiveVolumeException) FailureReason.MissingArchiveVolume(e.archiveName, e.volumeName) else ioFailureReasonOf(e)

    /**
     * 추출기가 역할을 준 파일 경로.
     * @param path 파일 경로
     * @param extractor 역할을 준 추출기
     * @param role 파일 이름의 역할
     */
    private data class NamedPath(val path: String, val extractor: ArchiveExtractor, val role: ArchiveNameRole) {
        /** 같은 아카이브의 볼륨을 묶는 값: 형식(추출기), 폴더, 세트. */
        val setKey: Triple<ArchiveExtractor, String?, String> get() = Triple(extractor, File(path).parent, role.set.id)
    }

    /**
     * 풀 아카이브 하나.
     * @param path 열 경로(단일 아카이브 또는 분할 압축의 시작 볼륨)
     * @param extractor 이 아카이브를 푸는 추출기
     * @param folderName 아카이브 폴더에 풀 때의 폴더 이름
     */
    private data class TargetArchive(val path: String, val extractor: ArchiveExtractor, val folderName: String)

    /** 압축 해제할 경로들을 아카이브로 묶은 결과. */
    private sealed interface ArchiveGrouping {

        /**
         * 모두 아카이브 하나로 묶인다.
         * @param archive 풀 아카이브
         */
        data class Archive(val archive: TargetArchive) : ArchiveGrouping

        /**
         * 모두 풀 수 있는 경로지만 아카이브 하나로 묶이지 않는다(없거나 둘 이상).
         * @param count 묶인 아카이브 수
         */
        data class NotOneArchive(val count: Int) : ArchiveGrouping

        /**
         * 풀 수 없는 경로가 있다.
         * @param path 처음 찾은 풀 수 없는 경로
         */
        data class Unextractable(val path: String) : ArchiveGrouping
    }
}
