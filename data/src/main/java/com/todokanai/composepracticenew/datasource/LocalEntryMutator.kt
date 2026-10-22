package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * 단일 로컬 항목의 이름변경·삭제·폴더 생성을 수행한다.
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
     */
    suspend fun rename(targetFile: String, newName: String, overwrite: Boolean) = withContext(Dispatchers.IO) {
        val source = File(targetFile)
        val dest = File(source.parentFile, newName)
        // 대소문자만 바꾸는 경우 대소문자 무시 저장소에서는 dest가 source 자신으로 보인다 — 충돌이 아니므로 그대로 바꾼다
        if (isSameEntry(source, dest)) {
            if (!source.renameTo(dest)) throw Exception("이름 변경 실패: ${source.name} → $newName")
            return@withContext
        }
        if (!dest.exists()) {
            // REPLACE_EXISTING 없이 이동 — 확인 직후 같은 이름이 생겨도 덮어쓰지 않고 실패한다
            runCatching { Files.move(source.toPath(), dest.toPath()) }
                .onFailure { e -> throw Exception("이름 변경 실패: ${source.name} → $newName (${e.message})") }
            return@withContext
        }
        if (!overwrite) throw Exception("이름 변경 실패: 같은 이름의 항목이 이미 있습니다 ($newName)")
        when {
            source.isDirectory && dest.isDirectory -> mergeDirectory(source, dest)
            !source.isDirectory && !dest.isDirectory ->
                Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            else -> throw Exception("이름 변경 실패: 종류가 다른 항목이 같은 이름으로 있습니다 ($newName)")
        }
    }

    /**
     * [source] 폴더의 내용을 [dest] 폴더에 합친 뒤 비게 된 [source]를 지운다. 겹치는 파일은 덮어쓰고 겹치지 않는 [dest]의 항목은 유지한다.
     * 하위 어디에서든 파일↔폴더 종류가 겹치면 아무것도 바꾸기 전에 실패한다.
     * @param source 이름을 바꿀 폴더
     * @param dest 같은 이름의 기존 폴더
     */
    private fun mergeDirectory(source: File, dest: File) {
        findTypeMismatch(source, dest)?.let { relative ->
            throw Exception("이름 변경 실패: 종류가 다른 항목이 같은 이름으로 있습니다 (${dest.name}/$relative)")
        }
        mergeInto(source, dest)
    }

    /**
     * [source] 트리를 [dest]에 겹쳐 보았을 때 파일↔폴더 종류가 다른 첫 항목을 찾는다.
     * @param source 합칠 폴더
     * @param dest 합쳐질 기존 폴더
     * @return 종류가 겹치는 항목의 [source] 기준 상대 경로, 없으면 null
     */
    private fun findTypeMismatch(source: File, dest: File): String? =
        source.walkTopDown().drop(1).firstNotNullOfOrNull { entry ->
            val counterpart = dest.toPath().resolve(source.toPath().relativize(entry.toPath())).toFile()
            source.slashRelativePathOf(entry).takeIf { counterpart.exists() && counterpart.isDirectory != entry.isDirectory }
        }

    /**
     * [source]의 각 항목을 [dest]로 옮긴다. 없는 항목은 그대로 옮기고, 겹치는 폴더는 재귀로 합치며, 겹치는 파일은 교체한다.
     * @param source 비울 폴더
     * @param dest 항목을 받을 폴더
     */
    private fun mergeInto(source: File, dest: File) {
        source.listFiles().orEmpty().forEach { child ->
            val target = File(dest, child.name)
            when {
                !target.exists() -> Files.move(child.toPath(), target.toPath())
                child.isDirectory -> mergeInto(child, target)
                else -> Files.move(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        if (!source.delete()) throw Exception("이름 변경 실패: 합친 뒤 원래 폴더를 지우지 못했습니다 (${source.name})")
    }

    /**
     * 두 경로가 같은 항목(같은 inode)을 가리키는지 확인한다.
     * @param a 비교할 경로
     * @param b 비교할 경로
     * @return 같은 항목이면 true
     */
    private fun isSameEntry(a: File, b: File): Boolean = runCatching { Files.isSameFile(a.toPath(), b.toPath()) }.getOrDefault(false)

    /**
     * [targetFile]과 하위 항목을 리프부터 삭제하며 항목 수 기준으로 진행률을 보고한다.
     * 지우지 못한 항목이 있어도 나머지를 끝까지 삭제한 뒤 지우지 못한 항목을 모아 예외로 알린다.
     * 지우지 못한 항목을 품은 상위 폴더는 비울 수 없으므로 시도하지도, 실패로 세지도 않는다(GNU rm과 같은 방식).
     * @param targetFile 삭제할 파일 또는 디렉터리의 절대 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun delete(targetFile: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(ProgressState(progress = 0))
        val root = File(targetFile)
        val files = root.walkBottomUp().toList()
        val total = files.size.coerceAtLeast(1)
        var processed = 0
        forEachCollectingFailures(
            files,
            "삭제하지 못함",
            // 대상 자신은 빈 경로 — 바깥 작업이 실패를 평탄화할 때 대상 이름 아래로 옮기므로 대상 이름 그대로 표시된다
            nameOf = { root.slashRelativePathOf(it) },
            isBlockedBy = { file, failed -> file.isAncestorOf(failed) }
        ) { file ->
            val deleted = deleteEntry(file)
            processed++
            onProgress(ProgressState(
                progress = processed * 100 / total,
                listSize = total,
                currentIndex = processed,
                currentFileName = file.name
            ))
            if (!deleted) throw IOException()
        }
    }

    /**
     * 항목 하나를 삭제한다. 이미 없는 항목은 삭제된 것으로 본다.
     * @param file 삭제할 파일 또는 빈 디렉터리
     * @return 삭제했거나 이미 없으면 true
     */
    private fun deleteEntry(file: File): Boolean = removeEntry(file) || !file.exists()

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
     */
    suspend fun makeDirectory(parentPath: String, name: String) = withContext(Dispatchers.IO) {
        val dir = File(parentPath, name)
        if (!dir.mkdir()) throw Exception("폴더 생성 실패: $name")
    }
}
