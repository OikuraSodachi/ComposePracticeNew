package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** 로컬 트리 항목의 종류. 심볼릭 링크를 따라가지 않고 항목 자신으로 정한다. */
internal enum class LocalEntryKind {
    /** 링크가 아닌 폴더. 순회가 안으로 들어간다. */
    DIRECTORY,
    /** 심볼릭 링크. 가리키는 대상과 무관하게 순회가 안으로 들어가지 않는다. */
    LINK,
    /** 그 밖의 항목(일반 파일, 특수 파일). 없는 항목도 여기에 든다(File.isDirectory처럼 확인 실패를 폴더 아님으로 본다). */
    FILE
}

/** 이 항목의 종류. 링크를 따라가지 않고 정하며, 디스크를 읽을 때마다 새로 구한다. */
internal val File.entryKind: LocalEntryKind
    get() = when {
        Files.isSymbolicLink(toPath()) -> LocalEntryKind.LINK
        Files.isDirectory(toPath(), LinkOption.NOFOLLOW_LINKS) -> LocalEntryKind.DIRECTORY
        else -> LocalEntryKind.FILE
    }

/**
 * 트리 순회가 내놓은 항목의 종류. [LocalEntryKind]에 순회가 안을 나열하지 못한 폴더를 더한 것이다 —
 * 순회 결과만 보는 쪽이 빈 폴더와 읽지 못한 폴더를 구분할 수 있도록(#422).
 * @param entryKind 나열 결과와 무관한 항목 자신의 종류
 */
internal enum class LocalTreeEntryKind(val entryKind: LocalEntryKind) {
    /** 안을 나열한 폴더. 그 하위 항목이 순회에 함께 나온다. */
    DIRECTORY(LocalEntryKind.DIRECTORY),
    /** 안을 나열하지 못한 폴더(읽을 권한 없음 등). 그 안의 항목은 순회에 나오지 않는다. */
    UNLISTED_DIRECTORY(LocalEntryKind.DIRECTORY),
    /** 심볼릭 링크. 순회가 안으로 들어가지 않는다. */
    LINK(LocalEntryKind.LINK),
    /** 그 밖의 항목(일반 파일, 특수 파일, 없는 항목). */
    FILE(LocalEntryKind.FILE)
}

/**
 * 로컬 트리를 훑어 나온 항목 하나.
 * @param file 항목
 * @param kind 훑을 때 정한 항목의 종류
 */
internal class LocalTreeEntry(val file: File, val kind: LocalTreeEntryKind)

/**
 * 이 항목과 그 아래 트리를 위에서 아래로 훑는다(폴더가 그 하위 항목보다 먼저 온다).
 * 로컬 작업의 트리 순회는 모두 이 함수나 [localTreeBottomUp]으로 한다 — 링크를 따라가지 않는 규칙을 한 곳에 두어, 순회를 새로 추가해도 링크를 따라가지 않도록.
 * 링크는 항목으로 내놓되 안으로 들어가지 않는다. 나열하지 못한 폴더는 [LocalTreeEntryKind.UNLISTED_DIRECTORY]로 내놓는다 —
 * File.walkTopDown처럼 폴더로 내놓고 안을 건너뛰면, 부르는 쪽이 그 폴더를 빈 폴더로 다뤄 내용이 빠진 채 성공을 보고하므로(#422).
 * @return 이 항목부터 시작하는 항목 순서
 */
internal fun File.localTreeTopDown(): Sequence<LocalTreeEntry> = sequence { visitTree(this@localTreeTopDown, topDown = true) }

/**
 * 이 항목과 그 아래 트리를 아래에서 위로 훑는다(하위 항목이 그 폴더보다 먼저 온다). 링크·나열 실패의 처리는 [localTreeTopDown]과 같다.
 * @return 이 항목으로 끝나는 항목 순서
 */
internal fun File.localTreeBottomUp(): Sequence<LocalTreeEntry> = sequence { visitTree(this@localTreeBottomUp, topDown = false) }

/**
 * [file]과 그 아래 항목을 차례로 내놓는다.
 * @param file 훑을 항목
 * @param topDown true면 폴더를 하위 항목보다 먼저, false면 나중에 내놓는다
 */
private suspend fun SequenceScope<LocalTreeEntry>.visitTree(file: File, topDown: Boolean) {
    val kind = file.entryKind
    val children = if (kind == LocalEntryKind.DIRECTORY) file.listFiles() else null
    val entry = LocalTreeEntry(file, treeKindOf(kind, listed = children != null))
    if (topDown) yield(entry)
    children?.forEach { visitTree(it, topDown) }
    if (!topDown) yield(entry)
}

/**
 * 항목 종류와 나열 결과로 순회 항목의 종류를 정한다.
 * @param kind 링크를 따라가지 않고 정한 항목의 종류
 * @param listed 폴더의 안을 나열했는지. 폴더가 아니면 쓰지 않는다
 * @return 순회 항목의 종류
 */
private fun treeKindOf(kind: LocalEntryKind, listed: Boolean): LocalTreeEntryKind = when (kind) {
    LocalEntryKind.DIRECTORY -> if (listed) LocalTreeEntryKind.DIRECTORY else LocalTreeEntryKind.UNLISTED_DIRECTORY
    LocalEntryKind.LINK -> LocalTreeEntryKind.LINK
    LocalEntryKind.FILE -> LocalTreeEntryKind.FILE
}

/**
 * 작업 대상 [root] 아래에서 건너뛴 링크 [link]를 실패 항목으로 만든다.
 * @param root 사용자가 선택한 작업 대상
 * @param link [root] 자신 또는 그 아래의 링크
 * @return [root]의 이름부터 시작하는 표시 경로와 링크 미지원 원인을 담은 실패 항목
 */
internal fun unsupportedLinkUnder(root: File, link: File): FailedItem = failedItemUnder(root, link, FailureReason.UnsupportedLink())

/**
 * 작업 대상 [root] 아래에서 안을 나열하지 못해 건너뛴 폴더 [folder]를 실패 항목으로 만든다.
 * @param root 사용자가 선택한 작업 대상
 * @param folder [root] 자신 또는 그 아래의 나열하지 못한 폴더
 * @return [root]의 이름부터 시작하는 표시 경로와 폴더를 읽지 못한 원인을 담은 실패 항목
 */
internal fun unlistedFolderUnder(root: File, folder: File): FailedItem = failedItemUnder(root, folder, FailureReason.FolderUnreadable())

/**
 * 작업 대상 [root] 아래의 [file]을 [reason] 실패 항목으로 만든다.
 * @param root 사용자가 선택한 작업 대상
 * @param file [root] 자신 또는 그 아래의 항목
 * @param reason 실패 원인
 * @return [root]의 이름부터 시작하는 표시 경로를 담은 실패 항목
 */
private fun failedItemUnder(root: File, file: File, reason: FailureReason): FailedItem {
    // 부모가 없는 경우는 파일시스템 루트를 선택한 것뿐이므로 루트 자신을 기준으로 한다
    val base = root.absoluteFile.parentFile ?: root.absoluteFile
    return FailedItem(base.slashRelativePathOf(file.absoluteFile), reason)
}
