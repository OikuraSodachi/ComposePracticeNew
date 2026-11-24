package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import java.io.File

/**
 * 압축 파일에 넣을 로컬 항목을 모은 결과.
 * @param entries 넣을 항목. 폴더 항목은 언제나 그 하위 항목보다 앞에 온다
 * @param skipped 넣지 않고 실패로 알릴 항목(링크, 안을 나열하지 못한 폴더)
 */
internal class CollectedArchiveSources(val entries: List<ArchiveSource>, val skipped: List<FailedItem>)

/**
 * [targetFiles]를 압축 파일에 넣을 항목으로 모은다. 압축과 압축 파일에 추가가 같은 규칙으로 모으도록 한 곳에 둔다.
 * 링크는 따라가지 않고 넣지 않으며, 안을 나열하지 못한 폴더도 빈 폴더로 넣지 않는다(#422).
 * 항목 이름은 선택 항목의 부모 폴더 기준 상대 경로다 — 선택한 파일은 `a.txt`, 선택한 폴더의 하위 파일은 `src/a.txt`.
 * @param targetFiles 넣을 파일·폴더 경로
 * @return 넣을 항목과 넣지 못한 항목
 */
internal fun collectArchiveSources(targetFiles: Collection<String>): CollectedArchiveSources {
    val entries = mutableListOf<ArchiveSource>()
    val skipped = mutableListOf<FailedItem>()
    // 위에서 아래로 순회하므로 폴더 항목이 항상 그 하위 항목보다 먼저 온다
    targetFiles.map(::File).forEach { root ->
        root.localTreeTopDown().forEach { entry ->
            when (entry.kind) {
                LocalTreeEntryKind.LINK -> skipped += unsupportedLinkUnder(root, entry.file)
                LocalTreeEntryKind.UNLISTED_DIRECTORY -> skipped += unlistedFolderUnder(root, entry.file)
                LocalTreeEntryKind.DIRECTORY, LocalTreeEntryKind.FILE -> entries += sourceOf(root, entry)
            }
        }
    }
    return CollectedArchiveSources(entries, skipped)
}

/**
 * 압축 파일에 넣는 트리 항목(나열한 폴더, 파일)을 아카이브 항목으로 만든다. 폴더인지와 파일 크기는 여기서 한 번만 정한다 — 진행 합계와 형식별 쓰기가 같은 값을 쓰도록.
 * @param root 사용자가 선택한 대상(파일 또는 폴더)
 * @param entry [root] 자신 또는 그 하위 항목
 * @return 폴더면 [ArchiveSource.Folder], 아니면 지금 크기를 담은 [ArchiveSource.Regular]
 */
private fun sourceOf(root: File, entry: LocalTreeEntry): ArchiveSource {
    val file = entry.file
    val name = entryNameOf(root, file)
    return if (entry.kind == LocalTreeEntryKind.DIRECTORY) ArchiveSource.Folder(name) else ArchiveSource.Regular(name, file, file.length())
}

/**
 * [file]의 항목 이름을 선택 항목 [root]의 부모 폴더 기준 상대 경로로 만든다.
 * @param root 사용자가 선택한 대상(파일 또는 폴더)
 * @param file [root] 자신 또는 그 하위 파일
 * @return `/`로 구분한 항목 이름
 */
private fun entryNameOf(root: File, file: File): String {
    // 부모가 없는 경우는 파일시스템 루트를 선택한 것뿐이므로 루트 자신을 기준으로 한다
    val base = root.absoluteFile.parentFile ?: root.absoluteFile
    return base.slashRelativePathOf(file.absoluteFile)
}
