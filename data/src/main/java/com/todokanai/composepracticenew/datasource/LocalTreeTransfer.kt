package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.ProgressMark
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.failedItemsAt
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import javax.inject.Inject

/**
 * 로컬 파일 트리의 복사와 이동(동일 파티션 rename / 교차 파티션 copy+delete)을 수행한다. 실패는 결과로 알린다.
 * 목적지가 원본 자신이거나 그 안쪽인 요청은 받지 않는다 — 이 위치 관계는 `LocalFileOperationUseCase`가 실행 전에 거부한다.
 * 같은 이름의 항목은 쓰는 시점에 파일 시스템이 알려 줄 때만 묻는다(`LocalConflictWrites.kt`). 폴더끼리 겹쳐 합치면 안쪽 항목을 다시 하나씩 판정한다.
 * @param isSamePartition 두 경로가 같은 파티션에 있는지 판별하는 함수. 앱에서는 OS 디바이스 ID로,
 * 테스트에서는 Android 프레임워크 없이 두 이동 경로를 고르도록 넘긴다
 * @param freeSpaceOf 경로가 속한 파티션의 여유 공간(bytes)을 돌려주는 함수. 테스트에서는 공간 부족을 재현하도록 넘긴다
 * @param syncToDisk 파일이나 폴더에 쓰인 내용을 장치에 기록하는 함수. 교차 파티션 이동이 원본을 지우기 전에 부른다.
 * 테스트에서는 폴더를 열 수 없는 OS(Windows)에서도 돌고 기록 순서를 확인하도록 넘긴다
 * @param leftovers 취소·실패로 멈춘 쓰기가 남긴 파일을 지우고, 지우지 못하면 알리는 LeftoverDiscarder
 */
class LocalTreeTransfer internal constructor(
    private val isSamePartition: (File, File) -> Boolean,
    private val freeSpaceOf: (File) -> Long,
    private val syncToDisk: (File) -> Unit,
    private val leftovers: LeftoverDiscarder
) {

    /** 실제 파일시스템을 판별하는 transfer를 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor(leftovers: LeftoverDiscarder) : this(LocalFileChecks::isSamePartition, File::getFreeSpace, ::forceToDisk, leftovers)

    /**
     * [targetFiles]를 [targetPath] 디렉터리 아래로 복사한다.
     * @param targetFiles 복사할 파일 경로 집합
     * @param targetPath 복사 대상 디렉터리 경로
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 복사 결과. 한 항목(폴더 안쪽 항목 포함)이 입출력 실패로 멈춰도 나머지 항목은 복사하고, 실패와 건너뛴 링크를 모아 알린다
     */
    suspend fun copy(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo copy@{
            val roots = targetFiles.map(::File)
            val totals = treeTotalsOf(roots)
            val totalBytes = roots.sumOf { totals.of(it).bytes }
            checkDiskSpace(totalBytes, freeSpaceOf(File(targetPath)))?.let { return@copy IoResult.ItemFailed(it) }
            val progress = TreeProgress(ByteProgressTracker(totalBytes, roots.sumOf { totals.of(it).fileCount }, onProgress), totals)
            progress.reportStart()

            forEachCollectingFailures(roots, nameOf = { it.name }) { root ->
                transferRoot(root) { failures -> copyEntry(root, File(targetPath, root.name), progress, resolveConflict, deleteSource = false, failures) }
            }.also { progress.reportComplete() }
        }
    }

    /**
     * [targetFiles]를 [targetPath] 디렉터리 아래로 이동한다. 건너뛴 항목은 원래 자리에 남는다.
     * @param targetFiles 이동할 파일 경로 집합
     * @param targetPath 이동 대상 디렉터리 경로
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 이동 결과. 한 항목(폴더 안쪽 항목 포함)이 입출력 실패로 멈춰도 나머지 항목은 옮기고, 실패와 건너뛴 링크를 모아 알린다. 실패한 항목과 그것을 품은 폴더는 원래 자리에 남는다
     */
    suspend fun move(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit): IoResult<Unit> = withContext(Dispatchers.IO) {
        localIo move@{
            val roots = targetFiles.map(::File)
            val destDir = File(targetPath)

            // 이동 전에 모든 트리를 미리 탐색해 크기를 확보 (rename 후에는 원본이 사라지므로)
            val totals = treeTotalsOf(roots)
            val samePartition = roots.map { isSamePartition(it, destDir) }

            val crossBytes = roots.indices
                .filter { !samePartition[it] }
                .sumOf { totals.of(roots[it]).bytes }
            if (crossBytes > 0) {
                val free = freeSpaceOf(destDir)
                if (free == 0L) return@move IoResult.ItemFailed(FailureReason.NoFreeSpace)
                checkDiskSpace(crossBytes, free)?.let { return@move IoResult.ItemFailed(it) }
            }
            val progress = TreeProgress(ByteProgressTracker(roots.sumOf { totals.of(it).bytes }, roots.sumOf { totals.of(it).fileCount }, onProgress), totals)
            progress.reportStart()

            forEachCollectingFailures(roots.zip(samePartition), nameOf = { (root, _) -> root.name }) { (root, samePartitionAsDest) ->
                val dest = File(targetPath, root.name)
                transferRoot(root) { failures ->
                    if (samePartitionAsDest) renameEntry(root, dest, progress, resolveConflict, failures)
                    else copyEntry(root, dest, progress, resolveConflict, deleteSource = true, failures)
                }
            }.also { progress.reportComplete() }
        }
    }

    /**
     * 선택 항목 [root]를 [transfer]로 옮기고, 옮기지 못한 항목을 [root] 기준 상대 경로의 실패로 모아 돌려준다(대상 자신은 빈 경로).
     * @param root 사용자가 선택한 대상
     * @param transfer 옮기지 못한 항목을 받은 목록에 넣으며 [root]를 옮기는 작업
     * @return 옮기지 못한 항목이 있으면 그 목록을 담은 실패, 없으면 성공
     */
    private inline fun transferRoot(root: File, transfer: (MutableList<EntryFailure>) -> Unit): IoResult<Unit> {
        val failures = mutableListOf<EntryFailure>()
        transfer(failures)
        return if (failures.isEmpty()) IoResult.Done
        else IoResult.PartiallyFailed(failures.flatMap { it.failure.failedItemsAt(root.slashRelativePathOf(it.file)) })
    }

    /**
     * 항목 [src] 하나를 옮기는 [step]을 실행하고, 입출력 실패면 [src]의 실패로 [failures]에 넣는다 —
     * 한 항목의 실패가 형제 항목과 다른 선택 항목을 멈추지 않도록, 실패를 항목마다 결과로 바꾼다(#502, #504).
     * 실패하면 [src]에서 아직 세지 않은 몫을 진행률에 넘긴다 — 건너뛴 항목처럼 처리한 것으로 세어, 진행이 멈춰 있다가 끝에 뛰지 않도록.
     * @param src 옮길 항목
     * @param progress 작업 전체의 진행률
     * @param failures 옮기지 못한 항목을 모으는 목록
     * @param step [src]를 옮기는 작업. 입출력 실패는 IOException으로 던진다
     * @return [step]의 결과. 실패했으면 false — [src]가 원래 자리에 남으므로 그것을 품은 폴더를 지우지 않도록
     */
    private inline fun attempting(src: File, progress: TreeProgress, failures: MutableList<EntryFailure>, step: () -> Boolean): Boolean {
        val mark = progress.mark()
        return localIo { IoResult.Success(step()) }.valueOr { failure ->
            failures += EntryFailure(src, failure)
            progress.passOverRest(src, mark)
            return false
        }
    }

    /**
     * [src]를 [target] 자리로 복사한다. 폴더는 안쪽 항목을 하나씩 복사하고, [deleteSource]면 다 옮긴 항목의 원본을 복사본과 그 이름을 장치에 기록한 뒤 지운다(교차 파티션 이동, #430).
     * 같은 이름의 항목이 있으면 [resolveConflict]에 묻는다. 건너뛴 항목(폴더면 그 안쪽 전체)은 진행률에서 처리한 것으로 넘기고 원본을 남긴다.
     * 링크는 따라가지 않고 건너뛰어 [failures]에 넣으며, 원본에 남는다. 목적지 자리에 링크가 있는 원본도 같이 건너뛴다.
     * 항목 하나가 입출력 실패로 멈추면 그 항목을 [failures]에 넣고 형제 항목을 계속 복사한다. 실패한 항목과 그것을 품은 폴더의 원본은 남는다.
     * 취소되면 쓰던 파일만 지우고, 이미 다 쓴 파일은 남긴다.
     * @return [src]의 모든 항목을 옮겼으면 true. 건너뛰거나 실패한 항목이 있으면 false
     */
    private suspend fun copyEntry(
        src: File,
        target: File,
        progress: TreeProgress,
        resolveConflict: WriteConflictResolver,
        deleteSource: Boolean,
        failures: MutableList<EntryFailure>
    ): Boolean = attempting(src, progress, failures) { copyEntryOrThrow(src, target, progress, resolveConflict, deleteSource, failures) }

    /**
     * [copyEntry]의 본문. [src] 자신의 입출력 실패는 IOException으로 던져 [copyEntry]가 [src]의 실패로 바꾼다.
     * @return [src]의 모든 항목을 옮겼으면 true. 건너뛰거나 실패한 항목이 있으면 false
     */
    private suspend fun copyEntryOrThrow(
        src: File,
        target: File,
        progress: TreeProgress,
        resolveConflict: WriteConflictResolver,
        deleteSource: Boolean,
        failures: MutableList<EntryFailure>
    ): Boolean {
        when (src.entryKind) {
            LocalEntryKind.LINK -> return false.also { failures += unsupportedLink(src) }
            LocalEntryKind.DIRECTORY -> {
                when (val slot = createDirectoryResolving(target) { resolveConflict.resolve(it) }) {
                    is WriteSlot.Ready -> Unit
                    WriteSlot.Skipped, WriteSlot.Link -> return passedOver(src, slot, progress, failures)
                }
                // 안쪽 원본을 지우기 전에 이 폴더의 이름을 장치에 남긴다 — 남기지 않으면 안쪽 파일을 기록해도 찾아갈 길이 없다
                if (deleteSource) syncToDisk(target.parentFile!!)
                val complete = childrenOf(src).fold(true) { complete, child ->
                    copyEntry(child, File(target, child.name), progress, resolveConflict, deleteSource, failures) && complete
                }
                if (complete && deleteSource) Files.delete(src.toPath())
                return complete
            }
            LocalEntryKind.FILE -> Unit
        }
        // 원본을 먼저 연다 — 덮어쓰기는 출력을 열 때 기존 내용을 비우므로, 원본을 열지 못하면 대상에 손대기 전에 실패해야 한다
        src.inputStream().use { input ->
            val output = when (val slot = openNewFileResolving(target) { resolveConflict.resolve(it) }) {
                is WriteSlot.Ready -> slot.value
                WriteSlot.Skipped, WriteSlot.Link -> return passedOver(src, slot, progress, failures)
            }
            progress.startFile()
            leftovers.deletingOnCancel(target) {
                output.use { progress.pump(input, it, src) }
            }
        }
        // 기록을 마친 뒤에 끝난 파일로 센다 — 큰 파일을 기록하는 동안 진행 표시가 끝난 것처럼 보이지 않도록
        if (deleteSource) persistCopy(target)
        progress.onFileCompleted(src)
        if (deleteSource && !src.delete()) throw IOException("원본 삭제 실패: ${src.name} — 대상에 복사본이 생성됐으나 원본이 남은 상태입니다")
        return true
    }

    /**
     * 다 쓴 복사본 [copy]의 내용과 그 이름(부모 폴더)을 장치에 기록한다. 원본을 지우기 전에 부른다 —
     * 닫은 출력은 OS 캐시에만 있을 수 있어, 지운 직후 전원이 끊기거나 매체가 빠지면 원본과 복사본을 함께 잃는다(#430).
     * @param copy 다 쓴 복사본
     * @throws IOException 기록하지 못했을 때 — 원본을 지우지 않고 실패로 알린다
     */
    private fun persistCopy(copy: File) {
        syncToDisk(copy)
        syncToDisk(copy.parentFile!!)
    }

    /**
     * 같은 파티션 안에서 [src]를 [target] 자리로 rename한다. 같은 이름의 항목이 있으면 [resolveConflict]에 묻는다.
     * 폴더끼리 겹쳐 합치면 안쪽 항목을 하나씩 옮기고, 다 옮긴 원본 폴더만 지운다. 건너뛴 항목은 원래 자리에 남는다.
     * 항목을 하나씩 다룰 때 만난 링크(목적지 자리의 링크 포함)는 건너뛰어 [failures]에 넣는다. 폴더를 통째로 rename하면 안쪽은 훑지 않으므로 안쪽 링크도 링크 그대로 함께 옮겨진다.
     * 항목 하나가 입출력 실패로 멈추면 그 항목을 [failures]에 넣고 형제 항목을 계속 옮긴다. 실패한 항목과 그것을 품은 폴더는 원래 자리에 남는다.
     * @return [src]의 모든 항목을 옮겼으면 true. 건너뛰거나 실패한 항목이 있으면 false
     */
    private suspend fun renameEntry(
        src: File,
        target: File,
        progress: TreeProgress,
        resolveConflict: WriteConflictResolver,
        failures: MutableList<EntryFailure>
    ): Boolean = attempting(src, progress, failures) { renameEntryOrThrow(src, target, progress, resolveConflict, failures) }

    /**
     * [renameEntry]의 본문. [src] 자신의 입출력 실패는 IOException으로 던져 [renameEntry]가 [src]의 실패로 바꾼다.
     * @return [src]의 모든 항목을 옮겼으면 true. 건너뛰거나 실패한 항목이 있으면 false
     */
    private suspend fun renameEntryOrThrow(
        src: File,
        target: File,
        progress: TreeProgress,
        resolveConflict: WriteConflictResolver,
        failures: MutableList<EntryFailure>
    ): Boolean {
        val kind = src.entryKind
        if (kind == LocalEntryKind.LINK) return false.also { failures += unsupportedLink(src) }
        if (kind == LocalEntryKind.DIRECTORY) {
            try {
                // 덮어쓰지 않는 rename — 확인 뒤에 같은 이름의 빈 폴더가 생겼으면 그 빈 폴더를 대신하고, 비어 있지 않은 폴더나 파일이면 실패한다
                Files.move(src.toPath(), target.toPath())
                // 옮긴 트리를 작업 전에 센 몫으로 넘긴다 — 합치기로 내려가는 경로는 안쪽 항목이 스스로 센다
                progress.passOver(src)
                return true
            } catch (e: FileAlreadyExistsException) {
                when (val slot = resolveExisting(target, sourceIsDirectory = true) { resolveConflict.resolve(it) }) {
                    is WriteSlot.Ready -> Unit
                    WriteSlot.Skipped, WriteSlot.Link -> return passedOver(src, slot, progress, failures)
                }
            }
            val complete = childrenOf(src).fold(true) { complete, child ->
                renameEntry(child, File(target, child.name), progress, resolveConflict, failures) && complete
            }
            if (complete) Files.delete(src.toPath())
            return complete
        }
        // 파일의 rename은 같은 이름의 파일을 말없이 대신하므로(POSIX rename), 먼저 "없을 때만 만들기"로 자리를 확보한 뒤 그 자리를 대신한다
        when (val slot = reserveFileResolving(target) { resolveConflict.resolve(it) }) {
            // 확보한 자리를 rename으로 한 번에 대신한다 — 옮기지 못하면 이 이동이 만든 빈 파일만 지운다
            is WriteSlot.Ready -> try {
                replaceFileAtomically(src, target)
            } catch (e: IOException) {
                if (slot.value != FileReservation.CREATED) throw e
                return abandonedReservation(src, target, e, progress, failures)
            }
            WriteSlot.Skipped, WriteSlot.Link -> return passedOver(src, slot, progress, failures)
        }
        progress.passOver(src)
        return true
    }

    /**
     * 파일 [src]를 확보한 빈 파일 [reservation] 자리로 rename하지 못했을 때, 이 이동이 만든 그 빈 파일을 지우고 [src]를 [failures]에 넣는다.
     * 빈 파일을 지우지 못하면 그 사실을 [src]의 실패 원인에 담는다 — 빈 파일이 옮긴 파일처럼 남지 않도록(#431). [src]는 원래 자리에 남는다.
     * @param src 옮기지 못한 파일
     * @param reservation 이 이동이 만든 빈 파일
     * @param error rename의 입출력 실패
     * @param progress 작업 전체의 진행률
     * @param failures 옮기지 못한 항목을 모으는 목록
     * @return 언제나 false — [src]를 옮기지 못했다
     */
    private fun abandonedReservation(src: File, reservation: File, error: IOException, progress: TreeProgress, failures: MutableList<EntryFailure>): Boolean {
        failures += EntryFailure(src, leftovers.discardInto(reservation, IoResult.ItemFailed(ioFailureReasonOf(error))))
        progress.passOver(src)
        return false
    }

    /**
     * 목적지 자리를 확보하지 못해 옮기지 않은 [src]를 진행률에서 처리한 것으로 넘긴다. 자리에 링크가 있었으면 [src]를 [failures]에 넣어 실패로 알린다.
     * @param src 옮기지 않은 원본(폴더면 그 안쪽 전체)
     * @param slot 확보하지 못한 결과([WriteSlot.Skipped] 또는 [WriteSlot.Link])
     * @param progress 작업 전체의 진행률
     * @param failures 옮기지 못한 항목을 모으는 목록
     * @return 언제나 false — [src]를 다 옮기지 못했다
     */
    private fun passedOver(src: File, slot: WriteSlot<*>, progress: TreeProgress, failures: MutableList<EntryFailure>): Boolean {
        progress.passOver(src)
        if (slot == WriteSlot.Link) failures += unsupportedLink(src)
        return false
    }

    /**
     * 링크라서 건너뛴 [src]의 실패를 만든다.
     * @param src 건너뛴 항목
     * @return [src]의 링크 실패
     */
    private fun unsupportedLink(src: File) = EntryFailure(src, IoResult.ItemFailed(FailureReason.UnsupportedLink()))

    /**
     * 폴더 [dir] 바로 아래 항목을 돌려준다.
     * @throws IOException 목록을 읽지 못했을 때 — 읽지 못한 폴더를 빈 폴더로 보고 지우지 않도록
     */
    private fun childrenOf(dir: File): List<File> = dir.listFiles()?.toList() ?: throw IOException("폴더를 읽지 못함: ${dir.name}")

    /**
     * [roots] 트리를 한 번 훑어 항목(파일·폴더)마다 파일 수와 바이트 합을 센다. 폴더의 몫은 그 안쪽 전체의 합이다.
     * 링크는 옮기지 않으므로, 나열하지 못한 폴더의 안쪽은 순회에 나오지 않으므로 세지 않는다.
     * @param roots 사용자가 선택한 대상
     * @return 항목별 몫
     */
    private fun treeTotalsOf(roots: List<File>): TreeTotals {
        val byEntry = HashMap<File, TreeTotal>()
        for (root in roots) for (entry in root.localTreeTopDown()) {
            when (entry.kind) {
                LocalTreeEntryKind.LINK -> Unit
                LocalTreeEntryKind.DIRECTORY, LocalTreeEntryKind.UNLISTED_DIRECTORY -> byEntry[entry.file] = TreeTotal.NONE
                LocalTreeEntryKind.FILE -> {
                    val file = TreeTotal(1, entry.file.length())
                    byEntry[entry.file] = file
                    // 선택 항목까지의 상위 폴더마다 이 파일의 몫을 더한다 — 위에서 아래로 훑으므로 상위 폴더는 이미 들어 있다
                    generateSequence(entry.file) { if (it == root) null else it.parentFile }.drop(1)
                        .forEach { dir -> byEntry[dir] = byEntry.getValue(dir) + file }
                }
            }
        }
        return TreeTotals(byEntry)
    }

    /**
     * 트리 하나의 파일 수와 바이트 합.
     * @param fileCount 폴더를 뺀 파일 수
     * @param bytes 파일 크기의 합
     */
    private class TreeTotal(val fileCount: Int, val bytes: Long) {
        /** 두 몫을 더한다. */
        operator fun plus(other: TreeTotal) = TreeTotal(fileCount + other.fileCount, bytes + other.bytes)

        companion object {
            /** 파일이 없는 몫(빈 폴더, 링크). */
            val NONE = TreeTotal(0, 0L)
        }
    }

    /**
     * 작업 전에 센 항목별 몫. 처리하는 동안 원본이 옮겨지거나 지워져 다시 훑을 수 없으므로, 처리하지 않은 항목의 몫은 여기서 찾는다.
     * @param byEntry 항목별 몫. 링크는 옮기지 않으므로 없다
     */
    private class TreeTotals(private val byEntry: Map<File, TreeTotal>) {
        /**
         * [entry]의 몫을 돌려준다.
         * @param entry 작업 전에 훑은 트리의 항목
         * @return [entry]의 몫. 링크면 [TreeTotal.NONE]
         */
        fun of(entry: File): TreeTotal = byEntry[entry] ?: TreeTotal.NONE
    }

    /**
     * 작업 전체의 진행률과 작업 전에 센 항목별 몫. 바이트를 복사하지 않고 처리한 항목(rename·건너뛰기·실패)의 몫을 미리 센 값으로 넘긴다.
     * 복사한 바이트와 넘긴 몫이 같은 진행률에 쌓여야 기준점 뒤의 모자란 몫이 맞으므로, 진행 보고는 모두 이 클래스를 거친다.
     * @param tracker 작업 전체의 진행률
     * @param totals 작업 전에 센 항목별 몫
     */
    private class TreeProgress(private val tracker: ByteProgressTracker, private val totals: TreeTotals) {
        /** 작업 시작 상태를 보고한다. */
        fun reportStart() = tracker.reportStart()

        /** 작업 완료를 보고한다. */
        fun reportComplete() = tracker.reportComplete()

        /**
         * 지금까지 센 몫을 기준점으로 돌려준다. 한 항목을 처리하기 전에 받아 두고 [passOverRest]에 넘긴다.
         * @return 지금의 기준점
         */
        fun mark(): ProgressMark = tracker.mark()

        /** 다음 파일 복사를 시작한다. */
        fun startFile() = tracker.startFile()

        /**
         * [input]을 [output]에 쓰면서 쓴 바이트를 진행률에 쌓는다.
         * @param input 원본 파일의 스트림
         * @param output 복사본의 스트림
         * @param src 복사 중인 원본 파일
         */
        fun pump(input: InputStream, output: OutputStream, src: File) = tracker.pump(input, output, src.name, src.length())

        /**
         * 파일 [src]의 복사를 마친 것으로 센다.
         * @param src 복사를 마친 원본 파일
         */
        fun onFileCompleted(src: File) = tracker.onFileCompleted(src.name, src.length())

        /**
         * [src] 트리 전체를 복사 없이 처리한 것으로 넘긴다(rename·건너뛰기).
         * @param src 처리한 원본 항목
         */
        fun passOver(src: File) = passOverRest(src, tracker.mark())

        /**
         * [mark] 뒤로 [src]에서 센 몫을 뺀 나머지를 처리한 것으로 넘긴다 — 도중에 실패한 항목의 남은 몫이 진행에서 빠지지 않도록.
         * @param src 처리를 마친 원본 항목
         * @param mark [src]를 처리하기 전에 받은 기준점
         */
        fun passOverRest(src: File, mark: ProgressMark) {
            val total = totals.of(src)
            tracker.catchUp(mark, total.fileCount, total.bytes, src.name)
        }
    }
}

/**
 * 옮기지 못한 항목과 그 실패.
 * @param file 옮기지 못한 원본 항목
 * @param failure 그 항목의 실패
 */
private class EntryFailure(val file: File, val failure: IoResult.Failure)
