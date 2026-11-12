package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 로컬 파일·디렉터리를 하나의 아카이브로 압축하는 흐름을 조율한다. 형식별 쓰기는 주입된 [ArchiveCompressor]에 위임한다.
 * @param compressors 형식을 키로 등록한 아카이브 쓰기 구현 — 한 형식에 구현 하나(같은 키의 중복 등록은 Dagger가 컴파일 오류로 막는다)
 */
class LocalArchiveCompression @Inject constructor(
    private val compressors: Map<ArchiveFormat, @JvmSuppressWildcards ArchiveCompressor>
) {

    /**
     * [compress]가 만들 수 있는 아카이브 형식을 구한다.
     * @return 구현이 등록된 형식의 집합
     */
    fun supportedFormats(): Set<ArchiveFormat> = compressors.keys

    /**
     * [targetFiles]를 [parentPath] 아래 `[baseName][ArchiveFormat.extension]` 아카이브로 압축한다. 그 이름이 이미 있으면 덮어쓰지 않고 `이름 (n).확장자` 형태의 새 이름으로 만든다.
     * @param targetFiles 압축 대상 파일 경로 집합
     * @param parentPath 아카이브를 만들 디렉터리 경로
     * @param baseName 만들 아카이브 이름(확장자 제외)
     * @param format 만들 아카이브 형식
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 압축 결과. [format]을 쓰는 구현이 없으면 아무것도 만들지 않고 실패한다
     */
    suspend fun compress(
        targetFiles: Set<String>,
        parentPath: String,
        baseName: String,
        format: ArchiveFormat,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo {
            val compressor = compressors[format] ?: return@localIo IoResult.ItemFailed("지원하지 않는 압축 형식: ${format.extension}")
            compressEntries(compressor, targetFiles, File(parentPath, "$baseName${format.extension}"), onProgress)
        }
    }

    /**
     * [compress]의 본체. 입출력 실패는 IOException으로 전파해 [compress]의 [localIo]가 결과로 바꾼다.
     * 링크는 따라가지 않고 아카이브에 넣지 않으며, 나머지 항목으로 아카이브를 만든 뒤 넣지 못한 링크를 실패로 알린다.
     * @param compressor 아카이브를 쓸 형식별 구현
     * @param targetFiles 압축 대상 파일 경로 집합
     * @param desired 만들 아카이브의 희망 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 압축 결과. 넣지 못한 링크가 있으면 [IoResult.PartiallyFailed]
     */
    private fun compressEntries(compressor: ArchiveCompressor, targetFiles: Set<String>, desired: File, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
        val roots = targetFiles.map(::File)
        val entries = mutableListOf<ArchiveSource>()
        val skippedLinks = mutableListOf<FailedItem>()
        // 위에서 아래로 순회하므로 폴더 항목이 항상 그 하위 항목보다 먼저 온다
        roots.forEach { root ->
            root.localTreeTopDown().forEach { entry ->
                if (entry.kind == LocalEntryKind.LINK) skippedLinks += unsupportedLinkUnder(root, entry.file)
                else entries += sourceOf(root, entry)
            }
        }
        if (entries.isEmpty()) {
            return if (skippedLinks.isEmpty()) IoResult.ItemFailed("압축할 항목이 없습니다.")
                   else IoResult.PartiallyFailed(SKIPPED_LINK_PREFIX, skippedLinks)
        }
        val files = entries.filterIsInstance<ArchiveSource.Regular>()
        val totalBytes = files.sumOf { it.bytes }
        checkDiskSpace(totalBytes, (desired.parentFile ?: desired).freeSpace)
            ?.let { return IoResult.ItemFailed(it) }
        val tracker = ByteProgressTracker(totalBytes, files.size, onProgress)
        tracker.reportStart()
        // 압축 대상 목록을 만든 뒤에 새 파일을 확보 — 기존 파일(선택 항목 자신 포함)은 절대 열지 않으므로 잘라내기·자기 참조가 생기지 않는다
        val output = createUniqueFile(desired)
        // 취소로 멈춘 아카이브는 일부 항목만 담긴 깨진 파일이므로 통째로 지운다 — 이 호출이 새로 만든 파일이라 기존 파일을 건드리지 않는다.
        // 완료 보고도 취소를 확인하므로 함께 감싼다 — 다 쓴 직후의 취소도 아카이브를 남기지 않아, 취소된 압축은 언제나 파일이 없다
        deletingOnCancel(output) {
            compressor.write(entries, output, tracker)
            tracker.reportComplete()
        }
        return if (skippedLinks.isEmpty()) IoResult.Done else IoResult.PartiallyFailed(SKIPPED_LINK_PREFIX, skippedLinks)
    }

    /**
     * 링크가 아닌 트리 항목을 아카이브 항목으로 만든다. 폴더인지와 파일 크기는 여기서 한 번만 정한다 — 진행 합계와 형식별 쓰기가 같은 값을 쓰도록.
     * @param root 사용자가 선택한 압축 대상(파일 또는 폴더)
     * @param entry [root] 자신 또는 그 하위 항목
     * @return 폴더면 [ArchiveSource.Folder], 아니면 지금 크기를 담은 [ArchiveSource.Regular]
     */
    private fun sourceOf(root: File, entry: LocalTreeEntry): ArchiveSource {
        val file = entry.file
        val name = entryNameOf(root, file)
        return if (entry.kind == LocalEntryKind.DIRECTORY) ArchiveSource.Folder(name) else ArchiveSource.Regular(name, file, file.length())
    }

    /**
     * [file]의 항목 이름을 선택 항목 [root]의 부모 폴더 기준 상대 경로로 만든다.
     * 선택한 파일은 `a.txt`, 선택한 폴더의 하위 파일은 `src/a.txt`처럼 선택 항목 이름부터 시작한다.
     * @param root 사용자가 선택한 압축 대상(파일 또는 폴더)
     * @param file [root] 자신 또는 그 하위 파일
     * @return `/`로 구분한 항목 이름
     */
    private fun entryNameOf(root: File, file: File): String {
        // 부모가 없는 경우는 파일시스템 루트를 선택한 것뿐이므로 루트 자신을 기준으로 한다
        val base = root.absoluteFile.parentFile ?: root.absoluteFile
        return base.slashRelativePathOf(file.absoluteFile)
    }

    /**
     * [desired] 경로에 새 파일을 만들어 반환한다. 이미 있으면 `이름 (1).확장자`, `이름 (2).확장자` … 순으로 비어 있는 이름을 찾는다.
     * 존재 확인과 생성을 [File.createNewFile] 한 번으로 처리해, 확인 직후 다른 곳에서 같은 이름이 생겨도 덮어쓰지 않는다.
     * @param desired 사용자가 지정한 생성 경로
     * @return 이 호출이 새로 만든 빈 파일
     */
    private fun createUniqueFile(desired: File): File =
        generateSequence(0) { it + 1 }
            .map { index -> numberedFile(desired, index) }
            .first { it.createNewFile() }

    /**
     * [desired]에 번호 [index]를 붙인 후보 경로를 만든다. 0이면 원래 경로 그대로다.
     * @param desired 사용자가 지정한 생성 경로
     * @param index 붙일 번호
     * @return 후보 파일 경로
     */
    private fun numberedFile(desired: File, index: Int): File {
        if (index == 0) return desired
        val baseNameEnd = FileNameParts.baseNameEnd(desired.name)
        return File(desired.parentFile, FileNameParts.numbered(desired.name.substring(0, baseNameEnd), desired.name.substring(baseNameEnd), index))
    }

    private companion object {
        /** 아카이브에 넣지 못한 링크를 알리는 실패 메시지 머리말. */
        const val SKIPPED_LINK_PREFIX = "압축에 넣지 못함"
    }
}
