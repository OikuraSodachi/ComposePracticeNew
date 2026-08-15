package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Implements file mutations (zip, copy, rename, delete, move) directly via Java I/O. */
@Singleton
class FileActionRepositoryImpl @Inject constructor() : FileActionRepository {

    override fun zipAction(targetFiles: List<String>, zipFile: String): Flow<ProgressState> = flow {
        val roots = targetFiles.map(::File)
        var totalBytesAcc = 0L
        val allFiles = roots.flatMap { root ->
            root.walkTopDown().filter { f -> !f.isDirectory }
                .onEach { totalBytesAcc += it.length() }
                .map { root to it }
                .toList()
        }
        if (allFiles.isEmpty()) { emit(ProgressState(error = "압축할 파일이 없습니다.")); return@flow }
        val totalBytes = totalBytesAcc.coerceAtLeast(1)
        checkDiskSpace(totalBytes, File(zipFile).parentFile ?: File(zipFile))
            ?.let { emit(ProgressState(error = it)); return@flow }
        val totalFileCount = allFiles.size
        var writtenBytes = 0L
        var prevProgress = -1
        var fileIndex = 0
        emit(ProgressState(progress = 0))

        ZipOutputStream(File(zipFile).outputStream()).use { zos ->
            allFiles.forEach { (root, sFile) ->
                fileIndex++
                val entryName = root.toPath().relativize(sFile.toPath())
                    .toString().replace("\\", "/")
                zos.putNextEntry(ZipEntry(entryName))
                sFile.inputStream().use { input ->
                    val (wb, pp) = pumpBytes(input, zos, totalBytes, writtenBytes, prevProgress) { written, progress ->
                        emit(ProgressState(
                            progress = progress,
                            totalBytes = totalBytes,
                            writtenBytes = written,
                            listSize = totalFileCount,
                            currentIndex = fileIndex,
                            currentFileName = sFile.name,
                            currentFileBytes = sFile.length()
                        ))
                    }
                    writtenBytes = wb
                    prevProgress = pp
                }
                zos.closeEntry()
            }
        }
        emit(ProgressState(
            progress = 100,
            totalBytes = totalBytes,
            writtenBytes = totalBytes,
            listSize = totalFileCount,
            currentIndex = totalFileCount
        ))
    }.flowOn(Dispatchers.IO)

    override fun copyAction(targetFiles: List<String>, targetPath: String): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))
        val roots = targetFiles.map(::File)
        var totalBytesAcc = 0L
        val allFiles = roots.flatMap { root ->
            root.walkTopDown().filter { f -> !f.isDirectory }
                .onEach { totalBytesAcc += it.length() }
                .toList()
        }
        val totalBytes = totalBytesAcc.coerceAtLeast(1)
        checkDiskSpace(totalBytes, File(targetPath))
            ?.let { emit(ProgressState(error = it)); return@flow }
        val totalFileCount = allFiles.size
        var writtenBytes = 0L
        var prevProgress = -1
        var fileIndex = 0

        roots.forEach { root ->
            val dest = File(targetPath, root.name)
            val (wb, pp, fi) = copyTree(root, dest, totalBytes, totalFileCount, writtenBytes, prevProgress, fileIndex) { src, written, progress, idx ->
                emit(ProgressState(
                    progress = progress,
                    totalBytes = totalBytes,
                    writtenBytes = written,
                    listSize = totalFileCount,
                    currentIndex = idx,
                    currentFileName = src.name,
                    currentFileBytes = src.length()
                ))
            }
            writtenBytes = wb
            prevProgress = pp
            fileIndex = fi
        }
        emit(ProgressState(
            progress = 100,
            totalBytes = totalBytes,
            writtenBytes = totalBytes,
            listSize = totalFileCount,
            currentIndex = totalFileCount
        ))
    }.flowOn(Dispatchers.IO)

    override fun renameFile(targetFile: String, newName: String): Flow<ProgressState> = flow {
        val file = File(targetFile)
        val success = file.renameTo(File("${file.parent}/$newName"))
        if (success) {
            emit(ProgressState(progress = 100))
        } else {
            emit(ProgressState(error = "이름 변경 실패: ${file.name} → $newName"))
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteFile(targetFile: String): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))
        val files = File(targetFile).walkBottomUp().toList()
        val total = files.size.coerceAtLeast(1)
        files.forEachIndexed { index, file ->
            file.delete()
            emit(ProgressState(
                progress = (index + 1) * 100 / total,
                listSize = total,
                currentIndex = index + 1,
                currentFileName = file.name
            ))
        }
    }.flowOn(Dispatchers.IO)

    override fun moveFile(targetFiles: List<String>, targetPath: String): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))
        val roots = targetFiles.map(::File)
        val destDir = File(targetPath)

        // 이동 전에 모든 트리를 미리 탐색해 크기를 확보 (rename 후에는 원본이 사라지므로)
        val treeEntries = roots.map { root ->
            root.walkTopDown().onEnter { it != File(targetPath, root.name) }.toList()
        }
        val leafSizes = treeEntries.map { list -> list.filter { !it.isDirectory }.sumOf { it.length() } }
        val leafCounts = treeEntries.map { list -> list.count { !it.isDirectory } }
        val totalBytes = sumBytesOrOne(leafSizes)
        val totalFileCount = leafCounts.sum()

        val crossBytes = roots.indices
            .filter { !isSamePartition(roots[it], destDir) }
            .sumOf { leafSizes[it] }
        if (crossBytes > 0) {
            if (destDir.freeSpace == 0L) { emit(ProgressState(error = "디스크 공간 부족: 여유 공간 없음")); return@flow }
            checkDiskSpace(crossBytes, destDir)?.let { emit(ProgressState(error = it)); return@flow }
        }

        var writtenBytes = 0L
        var prevProgress = -1
        var fileIndex = 0

        roots.forEachIndexed { i, root ->
            val dest = File(targetPath, root.name)
            if (isSamePartition(root, destDir)) {
                // 동일 파티션: rename syscall로 원자적 이동
                // REPLACE_EXISTING은 비어있지 않은 디렉터리를 대체하지 못하므로 실패 시 error emit
                runCatching {
                    Files.move(root.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }.onFailure { e ->
                    emit(ProgressState(error = "이동 실패: ${root.name} — ${e.message} (${i}개 항목은 이미 이동됨)"))
                    return@flow
                }
                writtenBytes += leafSizes[i]
                fileIndex += leafCounts[i]
                val progress = (writtenBytes * 100 / totalBytes).toInt()
                if (progress != prevProgress) {
                    emit(ProgressState(progress = progress, totalBytes = totalBytes, writtenBytes = writtenBytes, listSize = totalFileCount, currentIndex = fileIndex, currentFileName = root.name))
                    prevProgress = progress
                }
            } else {
                val (wb, pp, fi) = copyTree(root, dest, totalBytes, totalFileCount, writtenBytes, prevProgress, fileIndex, entries = treeEntries[i]) { srcFile, written, progress, idx ->
                    emit(ProgressState(progress = progress, totalBytes = totalBytes, writtenBytes = written, listSize = totalFileCount, currentIndex = idx, currentFileName = srcFile.name, currentFileBytes = srcFile.length()))
                }
                writtenBytes = wb
                prevProgress = pp
                fileIndex = fi
                if (!root.deleteRecursively()) {
                    emit(ProgressState(error = "원본 삭제 실패: ${root.name} — 대상에 복사본이 생성됐으나 원본이 남은 상태입니다 (앞선 ${i}개 항목은 이동 완료)"))
                    return@flow
                }
            }
        }
        emit(ProgressState(progress = 100, totalBytes = totalBytes, writtenBytes = totalBytes, listSize = totalFileCount, currentIndex = totalFileCount))
    }.flowOn(Dispatchers.IO)

    override fun unzipAction(zipFiles: List<String>, destPath: String, unzipHere: Boolean): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))

        // 전체 zip 파일의 압축 해제 용량 합산
        val byteSizes = mutableListOf<Long>()
        var totalFileCount = 0
        zipFiles.forEach { path ->
            ZipFile(path).use { zf ->
                val entries = zf.entries().toList()
                byteSizes += entries.sumOf { it.size }
                totalFileCount += entries.count { !it.isDirectory }
            }
        }
        val totalBytes = sumBytesOrOne(byteSizes)
        checkDiskSpace(totalBytes, File(destPath))?.let { emit(ProgressState(error = it)); return@flow }

        var writtenBytes = 0L
        var prevProgress = -1
        var fileIndex = 0
        var skippedEntries = 0

        zipFiles.forEach { zipFilePath ->
            ZipFile(zipFilePath).use { zf ->
                val entries = zf.entries().toList()
                val root = if (unzipHere) File(destPath)
                           else File(destPath, File(zipFilePath).nameWithoutExtension).also { it.mkdirs() }
                entries.forEach { entry ->
                    val target = root.resolve(entry.name)
                    if (!isUnderRoot(target, root)) { skippedEntries++; return@forEach }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        fileIndex++
                        zf.getInputStream(entry).use { input ->
                            target.outputStream().use { output ->
                                val (wb, pp) = pumpBytes(input, output, totalBytes, writtenBytes, prevProgress) { written, progress ->
                                    emit(ProgressState(progress = progress, totalBytes = totalBytes, writtenBytes = written, listSize = totalFileCount, currentIndex = fileIndex, currentFileName = entry.name, currentFileBytes = entry.size))
                                }
                                writtenBytes = wb
                                prevProgress = pp
                            }
                        }
                    }
                }
            }
        }
        if (skippedEntries > 0) {
            emit(ProgressState(error = "경로 검증 실패로 ${skippedEntries}개 항목을 건너뜀"))
        } else {
            emit(ProgressState(progress = 100))
        }
    }.flowOn(Dispatchers.IO)

    override fun makeDirectory(parentPath: String, name: String): Flow<ProgressState> = flow {
        val dir = File(parentPath, name)
        if (dir.mkdir()) {
            emit(ProgressState(progress = 100))
        } else {
            emit(ProgressState(error = "폴더 생성 실패: $name"))
        }
    }.flowOn(Dispatchers.IO)

    /** 두 파일이 동일한 파티션에 있는지 OS 레벨 디바이스 ID로 판별한다. 판별 실패 시 false를 반환해 copy+delete 경로를 선택한다. */
    private fun isSamePartition(a: File, b: File): Boolean = runCatching {
        Os.stat(a.canonicalPath).st_dev == Os.stat(b.canonicalPath).st_dev
    }.getOrDefault(false)

    /** 바이트 크기 목록의 합을 반환한다. 합이 0이면 1을 반환해 0 나눗셈을 방지한다. */
    private fun sumBytesOrOne(sizes: List<Long>) = sizes.sum().coerceAtLeast(1)

    /** [dest] 파티션의 여유 공간이 [needed] 바이트 미만이면 오류 메시지를 반환하고, 충분하면 null을 반환한다. */
    private fun checkDiskSpace(needed: Long, dest: File): String? {
        val free = dest.freeSpace
        return if (free < needed)
            "디스크 공간 부족: 필요 ${needed / 1024} KB, 여유 ${free / 1024} KB"
        else null
    }

    /** [file]의 정규화 경로가 [root] 하위에 있는지 확인한다. Zip Slip 방지용. */
    private fun isUnderRoot(file: File, root: File): Boolean {
        val rootPath = root.canonicalPath.let {
            if (it.endsWith(File.separator)) it else it + File.separator
        }
        return file.canonicalFile.path.startsWith(rootPath)
    }

    /** 버퍼 단위로 [input]을 읽어 [output]에 쓰면서 진행률이 바뀔 때만 [onProgress]를 호출한다. */
    private suspend fun pumpBytes(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        writtenBytes: Long,
        prevProgress: Int,
        progressScale: Int = 100,
        onProgress: suspend (written: Long, progress: Int) -> Unit
    ): Pair<Long, Int> {
        var wb = writtenBytes
        var pp = prevProgress
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            wb += read
            val progress = (wb * progressScale / totalBytes).toInt()
            if (progress != pp) {
                onProgress(wb, progress)
                pp = progress
            }
            read = input.read(buffer)
        }
        return wb to pp
    }

    /** [root] 트리를 [dest]로 재귀 복사하며 진행률이 바뀔 때만 [onProgress]를 호출한다. */
    private suspend fun copyTree(
        root: File,
        dest: File,
        totalBytes: Long,
        totalFileCount: Int,
        writtenBytes: Long,
        prevProgress: Int,
        fileIndex: Int,
        progressScale: Int = 100,
        entries: List<File>? = null,
        onProgress: suspend (src: File, written: Long, progress: Int, fileIndex: Int) -> Unit
    ): Triple<Long, Int, Int> {
        var wb = writtenBytes
        var pp = prevProgress
        var fi = fileIndex
        val walk = entries?.asSequence() ?: root.walkTopDown().onEnter { it != dest }
        walk.forEach { src ->
            val target = dest.toPath().resolve(root.toPath().relativize(src.toPath())).toFile()
            if (src.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                fi++
                src.inputStream().use { input ->
                    target.outputStream().use { output ->
                        val (newWb, newPp) = pumpBytes(input, output, totalBytes, wb, pp, progressScale) { written, progress ->
                            onProgress(src, written, progress, fi)
                        }
                        wb = newWb
                        pp = newPp
                    }
                }
            }
        }
        return Triple(wb, pp, fi)
    }
}
