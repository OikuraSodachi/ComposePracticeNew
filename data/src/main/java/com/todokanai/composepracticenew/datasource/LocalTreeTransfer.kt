package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * 로컬 파일 트리의 복사와 이동(동일 파티션 rename / 교차 파티션 copy+delete)을 수행한다. 실패는 결과로 알린다.
 * 목적지가 원본 자신이거나 그 안쪽인 요청은 받지 않는다 — 이 위치 관계는 `LocalFileOperationUseCase`가 실행 전에 거부한다.
 * 같은 이름의 항목은 쓰는 시점에 파일 시스템이 알려 줄 때만 묻는다(`LocalConflictWrites.kt`). 폴더끼리 겹쳐 합치면 안쪽 항목을 다시 하나씩 판정한다.
 * @param isSamePartition 두 경로가 같은 파티션에 있는지 판별하는 함수. 앱에서는 OS 디바이스 ID로,
 * 테스트에서는 Android 프레임워크 없이 두 이동 경로를 고르도록 넘긴다
 * @param freeSpaceOf 경로가 속한 파티션의 여유 공간(bytes)을 돌려주는 함수. 테스트에서는 공간 부족을 재현하도록 넘긴다
 */
class LocalTreeTransfer internal constructor(
    private val isSamePartition: (File, File) -> Boolean,
    private val freeSpaceOf: (File) -> Long
) {

    /** 실제 파일시스템을 판별하는 transfer를 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor() : this(LocalFileChecks::isSamePartition, File::getFreeSpace)

    /**
     * [targetFiles]를 [targetPath] 디렉터리 아래로 복사한다.
     * @param targetFiles 복사할 파일 경로 집합
     * @param targetPath 복사 대상 디렉터리 경로
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 복사 결과
     */
    suspend fun copy(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo copy@{
            val roots = targetFiles.map(::File)
            val totals = roots.map(::treeTotalOf)
            val totalBytes = totals.sumOf { it.bytes }
            checkDiskSpace(totalBytes, freeSpaceOf(File(targetPath)))?.let { return@copy IoResult.ItemFailed(it) }
            val tracker = ByteProgressTracker(totalBytes, totals.sumOf { it.fileCount }, onProgress)
            tracker.reportStart()

            roots.forEach { root -> copyEntry(root, File(targetPath, root.name), tracker, resolveConflict, deleteSource = false) }
            tracker.reportComplete()
            IoResult.Done
        }
    }

    /**
     * [targetFiles]를 [targetPath] 디렉터리 아래로 이동한다. 건너뛴 항목은 원래 자리에 남는다.
     * @param targetFiles 이동할 파일 경로 집합
     * @param targetPath 이동 대상 디렉터리 경로
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 이동 결과. 중간에 실패하면 앞선 항목은 이미 이동된 상태다
     */
    suspend fun move(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo move@{
            val roots = targetFiles.map(::File)
            val destDir = File(targetPath)

            // 이동 전에 모든 트리를 미리 탐색해 크기를 확보 (rename 후에는 원본이 사라지므로)
            val totals = roots.map(::treeTotalOf)
            val samePartition = roots.map { isSamePartition(it, destDir) }

            val crossBytes = roots.indices
                .filter { !samePartition[it] }
                .sumOf { totals[it].bytes }
            if (crossBytes > 0) {
                val free = freeSpaceOf(destDir)
                if (free == 0L) return@move IoResult.ItemFailed("디스크 공간 부족: 여유 공간 없음")
                checkDiskSpace(crossBytes, free)?.let { return@move IoResult.ItemFailed(it) }
            }
            val tracker = ByteProgressTracker(totals.sumOf { it.bytes }, totals.sumOf { it.fileCount }, onProgress)
            tracker.reportStart()

            roots.forEachIndexed { i, root ->
                val dest = File(targetPath, root.name)
                // 입출력 실패에 어느 항목에서 멈췄는지와 이미 옮긴 항목 수를 붙인다 — 두 이동 경로가 같은 문맥을 알린다
                localIo(describe = { e -> "이동 실패: ${root.name} — ${ioFailureMessageOf(e)} (${i}개 항목은 이미 이동됨)" }) step@{
                    if (samePartition[i]) renameEntry(root, dest, tracker, resolveConflict)
                    else copyEntry(root, dest, tracker, resolveConflict, deleteSource = true)
                    IoResult.Done
                }.valueOr { return@move it }
            }
            tracker.reportComplete()
            IoResult.Done
        }
    }

    /**
     * [src]를 [target] 자리로 복사한다. 폴더는 안쪽 항목을 하나씩 복사하고, [deleteSource]면 다 옮긴 항목의 원본을 지운다(교차 파티션 이동).
     * 같은 이름의 항목이 있으면 [resolveConflict]에 묻는다. 건너뛴 항목(폴더면 그 안쪽 전체)은 진행률에서 처리한 것으로 넘기고 원본을 남긴다.
     * 입출력 실패는 IOException으로 전파해 호출부의 [localIo]가 결과로 바꾼다. 취소되면 쓰던 파일만 지우고, 이미 다 쓴 파일은 남긴다.
     * @return [src]의 모든 항목을 옮겼으면 true. 건너뛴 항목이 있으면 false
     */
    private suspend fun copyEntry(src: File, target: File, tracker: ByteProgressTracker, resolveConflict: WriteConflictResolver, deleteSource: Boolean): Boolean {
        if (src.isDirectory) {
            if (!createDirectoryResolving(target) { resolveConflict.resolve(it) }) return false.also { tracker.passOver(src) }
            val complete = childrenOf(src).fold(true) { complete, child ->
                copyEntry(child, File(target, child.name), tracker, resolveConflict, deleteSource) && complete
            }
            if (complete && deleteSource) Files.delete(src.toPath())
            return complete
        }
        // 원본을 먼저 연다 — 덮어쓰기는 출력을 열 때 기존 내용을 비우므로, 원본을 열지 못하면 대상에 손대기 전에 실패해야 한다
        src.inputStream().use { input ->
            val output = openNewFileResolving(target) { resolveConflict.resolve(it) } ?: return false.also { tracker.passOver(src) }
            tracker.startFile()
            deletingOnCancel(target) {
                output.use { tracker.pump(input, it, src.name, src.length()) }
            }
        }
        tracker.onFileCompleted(src.name, src.length())
        if (deleteSource && !src.delete()) throw IOException("원본 삭제 실패: ${src.name} — 대상에 복사본이 생성됐으나 원본이 남은 상태입니다")
        return true
    }

    /**
     * 같은 파티션 안에서 [src]를 [target] 자리로 rename한다. 같은 이름의 항목이 있으면 [resolveConflict]에 묻는다.
     * 폴더끼리 겹쳐 합치면 안쪽 항목을 하나씩 옮기고, 다 옮긴 원본 폴더만 지운다. 건너뛴 항목은 원래 자리에 남는다.
     * @return [src]의 모든 항목을 옮겼으면 true. 건너뛴 항목이 있으면 false
     */
    private suspend fun renameEntry(src: File, target: File, tracker: ByteProgressTracker, resolveConflict: WriteConflictResolver): Boolean {
        if (src.isDirectory) {
            try {
                // 덮어쓰지 않는 rename — 확인 뒤에 같은 이름의 빈 폴더가 생겼으면 그 빈 폴더를 대신하고, 비어 있지 않은 폴더나 파일이면 실패한다
                Files.move(src.toPath(), target.toPath())
                // 옮긴 트리를 그 자리에서 센다 — 합치기로 내려가는 경로는 안쪽 항목이 스스로 세므로 미리 세지 않는다
                tracker.passOver(target)
                return true
            } catch (e: FileAlreadyExistsException) {
                if (resolveConflict.resolve(conflictAt(target, sourceIsDirectory = true)) != ConflictChoice.PROCEED) return false.also { tracker.passOver(src) }
            }
            val complete = childrenOf(src).fold(true) { complete, child ->
                renameEntry(child, File(target, child.name), tracker, resolveConflict) && complete
            }
            if (complete) Files.delete(src.toPath())
            return complete
        }
        if (!reserveName(target)) {
            if (resolveConflict.resolve(conflictAt(target, sourceIsDirectory = false)) != ConflictChoice.PROCEED) return false.also { tracker.passOver(src) }
            replaceWith(src, target)
        } else {
            // 확보한 빈 파일을 rename으로 한 번에 대신한다 — 옮기지 못하면 확보한 빈 파일을 지운다
            try {
                replaceWith(src, target)
            } catch (e: IOException) {
                target.delete()
                throw e
            }
        }
        tracker.passOver(src)
        return true
    }

    /**
     * [target] 자리에 빈 파일을 만들어 이름을 확보한다. 파일의 rename은 같은 이름의 파일을 말없이 대신하므로(POSIX rename),
     * 덮어쓰지 않는 이동은 먼저 "없을 때만 만들기"로 자리를 확보한 뒤 그 자리를 대신한다.
     * @return 확보했으면 true. 같은 이름의 항목이 이미 있으면 false
     */
    private fun reserveName(target: File): Boolean = try {
        Files.createFile(target.toPath())
        true
    } catch (e: FileAlreadyExistsException) {
        false
    }

    /**
     * [src] 파일을 [target] 자리로 rename해 그 자리의 파일을 한 번에 대신한다(지우고 옮기지 않는다 — 그 사이에 생긴 항목과 다시 겹치지 않도록).
     * [target]이 폴더이면 실패한다.
     */
    private fun replaceWith(src: File, target: File) {
        Files.move(src.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /**
     * 폴더 [dir] 바로 아래 항목을 돌려준다.
     * @throws IOException 목록을 읽지 못했을 때 — 읽지 못한 폴더를 빈 폴더로 보고 지우지 않도록
     */
    private fun childrenOf(dir: File): List<File> = dir.listFiles()?.toList() ?: throw IOException("폴더를 읽지 못함: ${dir.name}")

    /** [src] 트리 전체를 쓰지 않고 처리한 것으로 진행률에 넘긴다(건너뛰기). */
    private fun ByteProgressTracker.passOver(src: File) = passOver(treeTotalOf(src), src.name)

    /** [total]만큼의 파일과 바이트를 복사 없이 처리한 것으로 진행률에 넘긴다(rename·건너뛰기). */
    private fun ByteProgressTracker.passOver(total: TreeTotal, name: String) {
        skipFiles(total.fileCount)
        addBytes(total.bytes, name, fileBytes = null)
    }

    /** [root] 트리의 파일 수와 바이트 합. */
    private fun treeTotalOf(root: File): TreeTotal {
        val files = root.walkTopDown().filter { !it.isDirectory }.toList()
        return TreeTotal(files.size, files.sumOf { it.length() })
    }

    /**
     * 트리 하나의 파일 수와 바이트 합.
     * @param fileCount 폴더를 뺀 파일 수
     * @param bytes 파일 크기의 합
     */
    private class TreeTotal(val fileCount: Int, val bytes: Long)
}
