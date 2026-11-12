package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * 항목을 하나씩 다루는 로컬 작업(복사·이동·이름 변경의 합치기·압축·업로드)이 심볼릭 링크를 만나 건너뛸 때의 실패 원인.
 * 링크는 지원 범위 밖이다(#320) — 따라가면 선택하지 않은 데이터를 지우거나 옮기고, 순환 링크에서 끝나지 않는다.
 */
internal const val UNSUPPORTED_LINK_REASON = "심볼릭 링크는 지원하지 않습니다"

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
 * 로컬 트리를 훑어 나온 항목 하나.
 * @param file 항목
 * @param kind 훑을 때 정한 항목의 종류
 */
internal class LocalTreeEntry(val file: File, val kind: LocalEntryKind)

/**
 * 이 항목과 그 아래 트리를 위에서 아래로 훑는다(폴더가 그 하위 항목보다 먼저 온다).
 * 로컬 작업의 트리 순회는 모두 이 함수나 [localTreeBottomUp]으로 한다 — 링크를 따라가지 않는 규칙을 한 곳에 두어, 순회를 새로 추가해도 링크를 따라가지 않도록.
 * 링크는 항목으로 내놓되 안으로 들어가지 않는다. 나열하지 못한 폴더는 항목으로 내놓고 그 안은 건너뛴다(File.walkTopDown과 같다).
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
    val entry = LocalTreeEntry(file, file.entryKind)
    if (topDown) yield(entry)
    if (entry.kind == LocalEntryKind.DIRECTORY) file.listFiles()?.forEach { visitTree(it, topDown) }
    if (!topDown) yield(entry)
}

/**
 * 작업 대상 [root] 아래에서 건너뛴 링크 [link]를 실패 항목으로 만든다.
 * @param root 사용자가 선택한 작업 대상
 * @param link [root] 자신 또는 그 아래의 링크
 * @return [root]의 이름부터 시작하는 표시 경로와 링크 미지원 원인을 담은 실패 항목
 */
internal fun unsupportedLinkUnder(root: File, link: File): FailedItem {
    // 부모가 없는 경우는 파일시스템 루트를 선택한 것뿐이므로 루트 자신을 기준으로 한다
    val base = root.absoluteFile.parentFile ?: root.absoluteFile
    return FailedItem(base.slashRelativePathOf(link.absoluteFile), UNSUPPORTED_LINK_REASON)
}
