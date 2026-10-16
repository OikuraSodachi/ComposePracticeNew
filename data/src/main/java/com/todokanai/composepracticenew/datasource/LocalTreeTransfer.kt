package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.datasource.LocalFileChecks.isSamePartition
import com.todokanai.composepracticenew.datasource.LocalFileChecks.sumBytesOrOne
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/** 로컬 파일 트리의 복사와 이동(동일 파티션 rename / 교차 파티션 copy+delete)을 수행한다. */
class LocalTreeTransfer @Inject constructor() {

    /**
     * [targetFiles]를 [targetPath] 디렉터리 아래로 복사한다.
     * @param targetFiles 복사할 파일 경로 집합
     * @param targetPath 복사 대상 디렉터리 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun copy(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        val roots = targetFiles.map(::File)
        var totalBytesAcc = 0L
        val allFiles = roots.flatMap { root ->
            root.walkTopDown().filter { f -> !f.isDirectory }
                .onEach { totalBytesAcc += it.length() }
                .toList()
        }
        val totalBytes = totalBytesAcc.coerceAtLeast(1)
        checkDiskSpace(totalBytes, File(targetPath))
            ?.let { throw Exception(it) }
        onProgress(ProgressState(progress = 0))
        val tracker = ByteProgressTracker(totalBytes, allFiles.size, onProgress)

        roots.forEach { root -> copyTree(root, File(targetPath, root.name), tracker) }
        tracker.reportComplete()
    }

    /**
     * [targetFiles]를 [targetPath] 디렉터리 아래로 이동한다.
     * @param targetFiles 이동할 파일 경로 집합
     * @param targetPath 이동 대상 디렉터리 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun move(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        val roots = targetFiles.map(::File)
        val destDir = File(targetPath)

        // 이동 전에 모든 트리를 미리 탐색해 크기를 확보 (rename 후에는 원본이 사라지므로)
        val treeEntries = roots.map { root ->
            root.walkTopDown().onEnter { it != File(targetPath, root.name) }.toList()
        }
        val leafSizes = treeEntries.map { list -> list.filter { !it.isDirectory }.sumOf { it.length() } }
        val leafCounts = treeEntries.map { list -> list.count { !it.isDirectory } }
        val totalBytes = sumBytesOrOne(leafSizes)

        val crossBytes = roots.indices
            .filter { !isSamePartition(roots[it], destDir) }
            .sumOf { leafSizes[it] }
        if (crossBytes > 0) {
            if (destDir.freeSpace == 0L) throw Exception("디스크 공간 부족: 여유 공간 없음")
            checkDiskSpace(crossBytes, destDir)?.let { throw Exception(it) }
        }
        onProgress(ProgressState(progress = 0))
        val tracker = ByteProgressTracker(totalBytes, leafCounts.sum(), onProgress)

        roots.forEachIndexed { i, root ->
            val dest = File(targetPath, root.name)
            if (isSamePartition(root, destDir)) {
                // 동일 파티션: rename syscall로 원자적 이동
                // REPLACE_EXISTING은 비어있지 않은 디렉터리를 대체하지 못하므로 실패 시 throw
                runCatching {
                    Files.move(root.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }.onFailure { e ->
                    throw Exception("이동 실패: ${root.name} — ${e.message} (${i}개 항목은 이미 이동됨)")
                }
                tracker.skipFiles(leafCounts[i])
                tracker.addBytes(leafSizes[i], root.name, fileBytes = null)
            } else {
                copyTree(root, dest, tracker, entries = treeEntries[i])
                if (!root.deleteRecursively()) {
                    throw Exception("원본 삭제 실패: ${root.name} — 대상에 복사본이 생성됐으나 원본이 남은 상태입니다 (앞선 ${i}개 항목은 이동 완료)")
                }
            }
        }
        tracker.reportComplete()
    }

    /** [root] 트리를 [dest]로 재귀 복사하며 [tracker]로 진행률을 보고한다. [entries]가 주어지면 재탐색 없이 그 목록을 사용한다. */
    private fun copyTree(root: File, dest: File, tracker: ByteProgressTracker, entries: List<File>? = null) {
        val walk = entries?.asSequence() ?: root.walkTopDown().onEnter { it != dest }
        walk.forEach { src ->
            val target = dest.toPath().resolve(root.toPath().relativize(src.toPath())).toFile()
            if (src.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                tracker.startFile()
                src.inputStream().use { input ->
                    target.outputStream().use { output ->
                        tracker.pump(input, output, src.name, src.length())
                    }
                }
            }
        }
    }
}
