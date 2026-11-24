package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import net.lingala.zip4j.model.FileHeader

/**
 * 사본에 넣을 항목 하나.
 * @param target 압축 파일 안의 '/' 구분 경로(폴더도 끝에 '/'를 붙이지 않는다)
 * @param source 내용을 읽을 로컬 항목
 */
internal data class PlannedWrite(val target: String, val source: ArchiveSource)

/**
 * 사본에 할 일.
 * @param writes 넣을 항목. 폴더 항목은 그 하위 항목보다 앞에 온다
 * @param removals 새 항목으로 바꾸려고 지울 기존 파일 항목의 이름
 */
internal data class AppendPlan(val writes: List<PlannedWrite>, val removals: List<String>)

/**
 * ZIP에 넣을 항목마다 쓸지, 덮어쓸지, 건너뛸지를 정한다. 겹치면 [ask]에 묻는다: 폴더끼리는 묻지 않고 합치고, 파일끼리는 묻고, 종류가 엇갈리면 건너뛰기만 할 수 있다.
 * 건너뛴 폴더의 안쪽 항목은 묻지 않고 함께 건너뛴다. 정한 항목도 이름 목록에 더해, 넣는 항목끼리 이름이 겹쳐도 같은 길로 묻는다.
 * @param entries 넣을 항목. 폴더 항목은 그 하위 항목보다 앞에 온다
 * @param headers 원본의 항목
 * @param folderNames 압축 파일 루트부터 넣을 폴더까지의 폴더 이름들
 * @param archiveName 질문에 보일 압축 파일 이름
 * @param ask 겹침의 처리 방법을 묻는 함수
 * @return 쓸 항목과 지울 기존 항목
 */
internal fun appendPlanOf(
    entries: List<ArchiveSource>,
    headers: List<FileHeader>,
    folderNames: List<String>,
    archiveName: String,
    ask: (WriteConflict) -> ConflictChoice
): AppendPlan {
    val names = ExistingNames.of(headers)
    val prefix = prefixOf(folderNames)
    val writes = mutableListOf<PlannedWrite>()
    val removals = mutableListOf<String>()
    val skippedFolders = mutableListOf<String>()
    entries.forEach { entry ->
        if (skippedFolders.any { entry.name.startsWith("$it/") }) return@forEach
        val target = prefix + entry.name
        val existing = names.kindOf(target)
        when (existing) {
            null -> writes += PlannedWrite(target, entry)
            ExistingKind.FOLDER -> when (entry) {
                // 폴더끼리는 합친다 — 이미 있는 폴더 항목을 다시 쓰지 않는다
                is ArchiveSource.Folder -> Unit
                is ArchiveSource.Regular -> ask(conflictOf(target, archiveName, WriteConflictKind.TYPE_MISMATCH))
            }
            ExistingKind.FILE -> when (entry) {
                is ArchiveSource.Folder -> {
                    ask(conflictOf(target, archiveName, WriteConflictKind.TYPE_MISMATCH))
                    skippedFolders += entry.name
                }
                is ArchiveSource.Regular -> if (ask(conflictOf(target, archiveName, WriteConflictKind.FILE)) == ConflictChoice.PROCEED) {
                    removals += names.fileHeaderNamesOf(target)
                    writes += PlannedWrite(target, entry)
                }
            }
        }
        if (existing == null) names.add(target, entry is ArchiveSource.Folder)
    }
    return AppendPlan(writes, removals)
}

/**
 * 넣을 폴더의 항목 이름 앞부분을 만든다.
 * @param folderNames 압축 파일 루트부터의 폴더 이름들
 * @return 루트면 빈 문자열, 아니면 `a/b/`
 */
private fun prefixOf(folderNames: List<String>): String = folderNames.joinToString("") { "$it/" }

/**
 * 겹친 자리의 질문을 만든다.
 * @param target 겹친 항목의 압축 파일 안 경로
 * @param archiveName 압축 파일 이름
 * @param kind 겹침 종류
 * @return 질문. 폴더 경로는 압축 파일 이름부터 보인다
 */
private fun conflictOf(target: String, archiveName: String, kind: WriteConflictKind): WriteConflict {
    val parent = target.substringBeforeLast('/', missingDelimiterValue = "")
    return WriteConflict(target.substringAfterLast('/'), if (parent.isEmpty()) archiveName else "$archiveName/$parent", kind)
}

/** 원본에 이미 있는 항목 종류. */
private enum class ExistingKind { FILE, FOLDER }

/**
 * 원본 항목의 자리. 자리는 내용 보기·압축 해제와 같은 판정([extractionSegmentsOf])으로 정해, 화면에 보인 폴더·이름과 같은 자리에서 겹침을 찾는다(`./a/b`도 `a` 폴더의 `b`).
 * 폴더는 폴더 항목(`a/`)뿐 아니라 다른 항목의 경로에만 나타나는 폴더도 담는다 — 폴더 항목 없이 만든 ZIP도 많기 때문이다.
 * @param files 파일 자리와 그 자리에 놓인 원본 항목의 원래 이름들 — 한 자리에 항목이 여럿일 수 있다(같은 이름의 항목, 표기만 다른 이름)
 * @param folders 폴더 자리(끝의 '/' 없이)
 */
private class ExistingNames(private val files: MutableMap<String, MutableList<String>>, private val folders: MutableSet<String>) {

    /** [target] 자리에 있는 항목의 종류. 없으면 null. */
    fun kindOf(target: String): ExistingKind? = when {
        target in files -> ExistingKind.FILE
        target in folders -> ExistingKind.FOLDER
        else -> null
    }

    /** [target] 자리 원본 파일 항목의 원래 이름 모두(덮어쓸 때 모두 지운다 — 하나라도 남으면 옛 내용이 같은 자리에 남는다). */
    fun fileHeaderNamesOf(target: String): List<String> = files[target].orEmpty()

    /** 넣기로 정한 항목을 더한다. 그 경로의 상위 폴더도 함께 더한다. 원본에 없는 항목이라 지울 원래 이름은 없다. */
    fun add(target: String, isFolder: Boolean) {
        if (isFolder) folders += target else files.getOrPut(target) { mutableListOf() }
        folders += parentsOf(target)
    }

    companion object {
        /** [headers]의 자리를 모은다. 풀 수 없는 경로(절대 경로, 루트 위로 올라가는 경로)는 내용 보기처럼 어느 폴더에도 두지 않는다. */
        fun of(headers: List<FileHeader>): ExistingNames {
            val names = ExistingNames(HashMap(), HashSet())
            headers.forEach { header ->
                val path = extractionSegmentsOf(header.fileName)?.joinToString("/") ?: return@forEach
                if (header.isDirectory) names.folders += path else names.files.getOrPut(path) { mutableListOf() } += header.fileName
                names.folders += parentsOf(path)
            }
            return names
        }

        /** [path]의 상위 폴더 경로들. `a/b/c`면 `a`, `a/b`. */
        private fun parentsOf(path: String): List<String> {
            val segments = path.split('/')
            return (1 until segments.size).map { segments.subList(0, it).joinToString("/") }
        }
    }
}
