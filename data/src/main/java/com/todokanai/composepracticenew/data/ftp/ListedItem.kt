package com.todokanai.composepracticenew.data.ftp

import com.todokanai.fileexplorer.FileEntry

/**
 * 원격 목록이 알려준 것 하나. 해석하지 못한 줄도 버리지 않고 담는다 — 버리면 목록에서 항목이 말없이 빠져, 폴더 다운로드가 빠뜨린 채 성공으로 끝난다(#255).
 */
internal sealed interface ListedItem {

    /** 이 항목이 있는 자리의 경로. 항목은 그 경로, 해석하지 못한 줄은 무엇인지 몰라 그 줄이 나온 디렉터리다. 표시 이름으로는 쓰지 않는다([failureName]). */
    val locationPath: String

    /** 이 항목이 실패하면 지울 수 없게 되는 디렉터리를 가리는 데 쓰는 경로. 이 경로를 하위로 품은 디렉터리는 비울 수 없다. */
    val occupiedPath: String

    /** 디렉터리 항목이면 true. */
    val isDirectoryEntry: Boolean get() = this is Entry && entry.isDirectory

    /**
     * 실패 메시지에 쓸, [rootPath] 아래에서의 이름을 만든다.
     * @param rootPath 이 항목을 수집한 트리의 루트 경로 (ftp://server/path 형식)
     * @return [rootPath] 기준의 상대 이름
     */
    fun failureName(rootPath: String): String

    /**
     * 해석한 항목.
     * @param entry 항목
     */
    data class Entry(val entry: FileEntry) : ListedItem {
        override val locationPath: String get() = entry.path
        override val occupiedPath: String get() = entry.path
        override fun failureName(rootPath: String): String = relativeRemotePath(entry.path, rootPath)
    }

    /**
     * 해석하지 못한 목록 줄. 무엇인지 모르므로 그 줄이 나온 디렉터리 안을 차지한 것으로 본다.
     * MLSD의 심볼릭 링크 줄(`type=OS.unix=symlink`, `type=OS.unix=slink:<대상>`)도 commons-net이 해석하지 못해 여기에 든다 — 링크는 지원 범위 밖이다.
     * @param dirPath 그 줄을 받은 디렉터리의 경로 (ftp://server/path 형식)
     * @param line 받은 줄
     */
    data class Unreadable(val dirPath: String, val line: String) : ListedItem {
        /** 이름 대신 보여 줄 받은 줄. 이름을 모르므로 줄 자체로 어느 항목인지 알아보게 한다(#347). */
        val shownLine: String get() = line.trim()
        override val locationPath: String get() = dirPath
        override val occupiedPath: String get() = childFtpUriPrefix(dirPath)
        override fun failureName(rootPath: String): String =
            relativeRemotePath(dirPath, rootPath).let { dir -> if (dir.isEmpty()) shownLine else "$dir/$shownLine" }
    }
}
