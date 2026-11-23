package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveEntry
import com.todokanai.composepracticenew.model.ArchiveFolder

/**
 * 압축 파일 항목 목록(경로 목록)을 폴더 트리로 만든다.
 * 항목의 위치는 압축 해제([ExtractionRoot.targetOf])와 같은 글자 규칙으로 정한다 — 빈 세그먼트와 `.`는 무시하고 `..`는 앞 세그먼트를 지운다.
 * 압축 해제 위치 밖이나 루트 자신을 가리키는 항목은 루트에 [ArchiveEntry.UnsafePath]로 둔다(숨기지 않는다).
 * 폴더 항목이 없는 형식은 안쪽 항목의 경로로 폴더를 만든다. 같은 이름의 폴더는 합치고, 폴더가 아닌 항목은 이름이 같아도 모두 둔다.
 * 재귀하지 않는다 — 깊이 중첩된 경로에서 스택이 넘치지 않도록.
 * @param entries [ArchiveExtractor.entries]가 읽은 항목 목록
 * @return 루트 폴더
 */
internal fun archiveTreeOf(entries: List<ArchiveEntryInfo>): ArchiveFolder {
    val root = FolderNode(name = "", lastModifiedMillis = null)
    // 만든 순서 — 하위 폴더는 항상 상위 폴더보다 뒤에 만들어지므로, 거꾸로 변환하면 하위가 먼저 끝난다
    val created = mutableListOf(root)
    for (entry in entries) {
        val segments = extractionSegmentsOf(entry.path)
        if (segments == null) {
            root.items += Item.Leaf(ArchiveEntry.UnsafePath(entry.path, entry.lastModifiedMillis))
            continue
        }
        val parent = segments.dropLast(1).fold(root) { folder, name -> folder.childFolder(name, created) }
        val name = segments.last()
        when (entry.kind) {
            ArchiveEntryKind.FOLDER -> parent.childFolder(name, created).apply { lastModifiedMillis = entry.lastModifiedMillis ?: lastModifiedMillis }
            ArchiveEntryKind.FILE -> parent.items += Item.Leaf(ArchiveEntry.File(name, entry.size, entry.lastModifiedMillis, entry.isEncrypted))
            ArchiveEntryKind.LINK -> parent.items += Item.Leaf(ArchiveEntry.Link(name, entry.lastModifiedMillis))
        }
    }
    for (node in created.asReversed()) node.result = ArchiveFolder(node.items.map(Item::toEntry))
    return checkNotNull(root.result)
}

/**
 * [path]를 압축 해제와 같은 글자 규칙으로 정리한 세그먼트로 나눈다. 링크는 보지 않는다(글자로만 정리).
 * @param path 압축 파일 안의 '/' 구분 경로
 * @return 루트부터의 세그먼트. 절대 경로이거나, 루트 위로 올라가거나, 루트 자신을 가리키면 null
 */
internal fun extractionSegmentsOf(path: String): List<String>? {
    if (path.startsWith('/')) return null
    val segments = ArrayDeque<String>()
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> continue
            ".." -> segments.removeLastOrNull() ?: return null
            else -> segments.addLast(segment)
        }
    }
    return segments.toList().ifEmpty { null }
}

/**
 * 트리를 만드는 동안의 폴더.
 * @param name 폴더 이름
 * @param lastModifiedMillis 폴더 항목의 수정 시각. 폴더 항목이 없으면 null
 */
private class FolderNode(val name: String, var lastModifiedMillis: Long?) {
    /** 바로 아래 항목(압축 파일에 담긴 순서). */
    val items = mutableListOf<Item>()

    /** 이름별 바로 아래 폴더 — 같은 이름의 폴더를 합친다. */
    private val folders = HashMap<String, FolderNode>()

    /** 변환한 폴더. 하위 폴더가 모두 변환된 뒤에 정해진다. */
    var result: ArchiveFolder? = null

    /**
     * 바로 아래의 [name] 폴더를 찾고, 없으면 만들어 [created]에 넣는다.
     * @param name 폴더 이름
     * @param created 만든 순서의 폴더 목록
     * @return 폴더
     */
    fun childFolder(name: String, created: MutableList<FolderNode>): FolderNode =
        folders.getOrPut(name) {
            FolderNode(name, lastModifiedMillis = null).also {
                items += Item.Sub(it)
                created += it
            }
        }
}

/** 트리를 만드는 동안 폴더 안에 놓인 항목. */
private sealed interface Item {
    /** 완성된 항목으로 바꾼다. 하위 폴더는 이미 변환돼 있어야 한다. */
    fun toEntry(): ArchiveEntry

    /** 폴더가 아닌 항목. */
    class Leaf(val entry: ArchiveEntry) : Item {
        override fun toEntry(): ArchiveEntry = entry
    }

    /** 하위 폴더. */
    class Sub(val node: FolderNode) : Item {
        override fun toEntry(): ArchiveEntry = ArchiveEntry.Folder(node.name, node.lastModifiedMillis, checkNotNull(node.result) { "하위 폴더가 먼저 변환되지 않았다" })
    }
}
