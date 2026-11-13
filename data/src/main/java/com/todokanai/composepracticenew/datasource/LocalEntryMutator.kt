package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * 로컬 항목의 이름변경·삭제(여러 대상을 하나의 작업으로)·폴더 생성을 수행한다. 실패는 결과로 알린다.
 * @param removeEntry 파일 또는 빈 폴더 하나를 지우고 성공 여부를 돌려주는 함수. 앱에서는 [File.delete]를,
 * 테스트에서는 OS·권한과 무관하게 삭제 실패를 재현하는 함수를 넘긴다
 */
class LocalEntryMutator internal constructor(private val removeEntry: (File) -> Boolean) {

    /** 실제 파일시스템에서 삭제하는 mutator를 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor() : this(File::delete)

    /**
     * [targetFile]의 이름을 [newName]으로 변경한다.
     * 같은 이름의 다른 항목이 있으면 [overwrite]가 false일 때 실패한다. true면 파일은 기존 파일을 교체하고,
     * 폴더는 기존 폴더에 합친다(겹치는 항목은 덮어쓰고 나머지는 유지). 파일↔폴더처럼 종류가 다르면 항상 실패한다.
     * @param targetFile 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 이름
     * @param overwrite 같은 이름의 항목을 덮어쓸지 여부
     * @return 이름 변경 결과
     */
    suspend fun rename(targetFile: String, newName: String, overwrite: Boolean): IoResult<Unit> = withContext(Dispatchers.IO) {
        val source = File(targetFile)
        localIo(describe = { e -> "이름 변경 실패: ${source.name} → $newName (${ioFailureMessageOf(e)})" }) {
            renameEntry(source, newName, overwrite)
        }
    }

    /**
     * [rename]의 본체. 입출력 실패는 IOException으로 전파해 [rename]의 [localIo]가 이름 변경 실패 메시지로 바꾼다.
     * @param source 이름을 변경할 항목
     * @param newName 변경할 새 이름
     * @param overwrite 같은 이름의 항목을 덮어쓸지 여부
     * @return 이름 변경 결과
     */
    private fun renameEntry(source: File, newName: String, overwrite: Boolean): IoResult<Unit> {
        val dest = File(source.parentFile, newName)
        // 대소문자만 바꾸는 경우 대소문자 무시 저장소에서는 dest가 source 자신으로 보인다 — 충돌이 아니므로 그대로 바꾼다.
        // 어느 쪽이 링크면 같은 항목 판정이 링크를 따라가 링크와 그 대상을 같은 항목으로 보므로, 이 지름길을 타지 않는다(대상을 링크로 대신하지 않도록)
        if (source.entryKind != LocalEntryKind.LINK && dest.entryKind != LocalEntryKind.LINK && isSameEntry(source, dest)) {
            return if (source.renameTo(dest)) IoResult.Done else IoResult.ItemFailed("이름 변경 실패: ${source.name} → $newName")
        }
        if (!dest.exists()) {
            // REPLACE_EXISTING 없이 이동 — 확인 직후 같은 이름이 생겨도 덮어쓰지 않고 실패한다
            Files.move(source.toPath(), dest.toPath())
            return IoResult.Done
        }
        if (!overwrite) return IoResult.ItemFailed("이름 변경 실패: 같은 이름의 항목이 이미 있습니다 ($newName)")
        val sourceKind = source.entryKind
        val destKind = dest.entryKind
        return when {
            // 링크를 합치거나 덮어쓰면 링크가 가리키는 폴더 안을 옮기거나, 폴더로 보이던 링크를 말없이 바꾼다
            sourceKind == LocalEntryKind.LINK || destKind == LocalEntryKind.LINK -> IoResult.ItemFailed("이름 변경 실패: $UNSUPPORTED_LINK_REASON ($newName)")
            sourceKind == LocalEntryKind.DIRECTORY && destKind == LocalEntryKind.DIRECTORY -> mergeDirectory(source, dest)
            sourceKind == LocalEntryKind.FILE && destKind == LocalEntryKind.FILE -> {
                Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                IoResult.Done
            }
            else -> IoResult.ItemFailed("이름 변경 실패: 종류가 다른 항목이 같은 이름으로 있습니다 ($newName)")
        }
    }

    /**
     * [source] 폴더의 내용을 [dest] 폴더에 합친 뒤 비게 된 [source]를 지운다. 겹치는 파일은 덮어쓰고 겹치지 않는 [dest]의 항목은 유지한다.
     * 하위 어디에서든 파일↔폴더 종류가 겹치거나 링크가 있으면 아무것도 바꾸기 전에 실패한다.
     * @param source 이름을 바꿀 폴더
     * @param dest 같은 이름의 기존 폴더
     * @return 합치기 결과
     */
    private fun mergeDirectory(source: File, dest: File): IoResult<Unit> {
        findMergeBlocker(source, dest)?.let { reason -> return IoResult.ItemFailed("이름 변경 실패: $reason") }
        return mergeInto(source, dest)
    }

    /**
     * [source] 트리를 [dest]에 겹쳐 보았을 때 합칠 수 없는 첫 항목을 찾는다. [source]의 링크와, 겹치는 자리의 [dest] 링크는 합칠 수 없다.
     * @param source 합칠 폴더
     * @param dest 합쳐질 기존 폴더
     * @return 합칠 수 없는 원인과 그 항목의 경로, 없으면 null
     */
    private fun findMergeBlocker(source: File, dest: File): String? =
        source.localTreeTopDown().drop(1).firstNotNullOfOrNull { entry ->
            val counterpart = dest.toPath().resolve(source.toPath().relativize(entry.file.toPath()))
            val counterpartKind = counterpart.toFile().entryKind.takeIf { Files.exists(counterpart, LinkOption.NOFOLLOW_LINKS) }
            val location = "${dest.name}/${source.slashRelativePathOf(entry.file)}"
            when {
                entry.kind == LocalEntryKind.LINK || counterpartKind == LocalEntryKind.LINK -> "$UNSUPPORTED_LINK_REASON ($location)"
                counterpartKind != null && counterpartKind != entry.kind -> "종류가 다른 항목이 같은 이름으로 있습니다 ($location)"
                else -> null
            }
        }

    /**
     * [source]의 각 항목을 [dest]로 옮긴다. 없는 항목은 그대로 옮기고, 겹치는 폴더는 재귀로 합치며, 겹치는 파일은 교체한다.
     * @param source 비울 폴더
     * @param dest 항목을 받을 폴더
     * @return 합치기 결과
     */
    private fun mergeInto(source: File, dest: File): IoResult<Unit> {
        for (child in source.listFiles().orEmpty()) {
            val target = File(dest, child.name)
            when {
                !target.exists() -> Files.move(child.toPath(), target.toPath())
                child.entryKind == LocalEntryKind.DIRECTORY -> mergeInto(child, target).valueOr { return it }
                else -> Files.move(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return if (source.delete()) IoResult.Done
               else IoResult.ItemFailed("이름 변경 실패: 합친 뒤 원래 폴더를 지우지 못했습니다 (${source.name})")
    }

    /**
     * 두 경로가 같은 항목(같은 inode)을 가리키는지 확인한다.
     * @param a 비교할 경로
     * @param b 비교할 경로
     * @return 같은 항목이면 true
     */
    private fun isSameEntry(a: File, b: File): Boolean =
        try {
            Files.isSameFile(a.toPath(), b.toPath())
        } catch (e: IOException) {
            // 어느 쪽이 없으면 같은 항목일 수 없다
            false
        }

    /**
     * [targetFiles]와 각 하위 항목을 하나의 작업으로 리프부터 삭제하며, 모든 대상의 항목 수 합계 기준으로 진행률을 보고한다.
     * 대상을 한 번에 받는 것은 진행을 작업 전체 기준으로 보고하기 위해서다 — 대상마다 따로 계산하면 진행이 대상마다 0%로 돌아간다(#355).
     * 지우지 못한 항목이 있어도 나머지를 끝까지 삭제한 뒤 지우지 못한 항목을 모아 알린다.
     * 지우지 못한 항목을 품은 상위 폴더는 비울 수 없으므로 시도하지도, 실패로 세지도 않는다(GNU rm과 같은 방식).
     * 링크는 따라가지 않고 링크 자신만 지운다 — 링크가 가리키는 대상은 선택한 항목이 아니다(대상 자신이 링크여도 같다).
     * @param targetFiles 삭제할 파일 또는 디렉터리의 절대 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 삭제 결과. 일부 항목을 지우지 못하면 대상 이름 아래 실패를 모은 [IoResult.PartiallyFailed]
     */
    suspend fun delete(targetFiles: Set<String>, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        onProgress(ProgressState(progress = 0))
        val trees = targetFiles.map { File(it).let { root -> root to root.localTreeBottomUp().map { entry -> entry.file }.toList() } }
        val progress = LocalDeletionProgress(trees.sumOf { (_, files) -> files.size }, onProgress)
        forEachCollectingFailures(trees, "삭제 실패", nameOf = { (root, _) -> root.name }) { (root, files) ->
            deleteTree(root, files, progress)
        }
    }

    /**
     * [root]의 트리 항목 [files]를 리프부터 삭제한다. 지우지 못한 항목을 품은 상위 폴더는 시도하지 않는다.
     * @param root 삭제 대상
     * @param files [root]와 하위 항목, 리프부터의 순서
     * @param progress 작업 전체의 삭제 진행
     * @return 삭제 결과. 지우지 못한 항목은 [root] 기준 상대 경로로 모은다(대상 자신은 빈 경로)
     */
    private suspend fun deleteTree(root: File, files: List<File>, progress: LocalDeletionProgress): IoResult<Unit> =
        forEachCollectingFailures(
            files,
            "삭제 실패",
            // 대상 자신은 빈 경로 — 바깥 작업이 실패를 평탄화할 때 대상 이름 아래로 옮기므로 대상 이름 그대로 표시된다
            nameOf = { root.slashRelativePathOf(it) },
            isBlockedBy = { file, failed -> file.isAncestorOf(failed) }
        ) { file ->
            val deleted = deleteEntry(file)
            progress.afterDeleting(file.name)
            // File.delete는 실패 원인을 알려주지 않는다
            if (deleted) IoResult.Done else IoResult.ItemFailed(null)
        }

    /**
     * 항목 하나를 삭제한다. 이미 없는 항목은 삭제된 것으로 본다.
     * 남아 있는지는 링크를 따라가지 않고 본다 — 지우지 못한 끊긴 링크를 없는 항목으로 보지 않도록.
     * @param file 삭제할 파일, 빈 디렉터리 또는 링크
     * @return 삭제했거나 이미 없으면 true
     */
    private fun deleteEntry(file: File): Boolean = removeEntry(file) || Files.notExists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    /**
     * 이 항목이 [entry]를 하위 항목으로 품는 폴더인지 판별한다.
     * @param entry 하위 후보 항목
     * @return [entry]가 이 항목 아래에 있으면 true
     */
    private fun File.isAncestorOf(entry: File): Boolean = entry.toPath() != toPath() && entry.toPath().startsWith(toPath())

    /**
     * [parentPath] 아래에 [name] 디렉터리를 생성한다.
     * @param parentPath 새 폴더를 생성할 부모 디렉터리 경로
     * @param name 생성할 폴더 이름
     * @return 생성 결과
     */
    suspend fun makeDirectory(parentPath: String, name: String): IoResult<Unit> = withContext(Dispatchers.IO) {
        if (File(parentPath, name).mkdir()) IoResult.Done else IoResult.ItemFailed("폴더 생성 실패: $name")
    }
}

/**
 * 삭제 작업 하나의 진행을 모든 대상의 항목 수 합계 기준으로 보고한다.
 * @param total 작업 전체에서 지울 항목 수. 0이면 1로 본다
 * @param onProgress 진행 상태를 수신하는 콜백
 */
private class LocalDeletionProgress(total: Int, private val onProgress: (ProgressState) -> Unit) {
    /** 진행률을 나눌 항목 수. */
    private val total = total.coerceAtLeast(1)

    /** 삭제를 시도한 항목 수. */
    private var processed = 0

    /** [name]의 삭제를 시도한 뒤 진행을 보고한다. */
    fun afterDeleting(name: String) {
        processed++
        onProgress(ProgressState(progress = processed * 100 / total, listSize = total, currentIndex = processed, currentFileName = name))
    }
}
