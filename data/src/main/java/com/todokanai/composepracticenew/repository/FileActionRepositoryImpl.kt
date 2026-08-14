package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.tools.independent.getPhysicalStorage_td
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
        val allFiles = roots.flatMap { root ->
            root.walkTopDown().filter { f -> !f.isDirectory }.map { root to it }.toList()
        }
        val totalBytes = allFiles.sumOf { (_, f) -> f.length() }.coerceAtLeast(1)
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
        val allFiles = roots.flatMap { it.walkTopDown().filter { f -> !f.isDirectory }.toList() }
        val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1)
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

    override fun moveFile(targetFile: String, targetPath: String): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))
        val src = File(targetFile)
        val dest = File(targetPath, src.name)

        if (getPhysicalStorage_td(src) == getPhysicalStorage_td(File(targetPath))) {
            // 동일 파티션: rename syscall로 원자적 이동, 추가 공간 불필요
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            emit(ProgressState(progress = 100))
        } else {
            val allFiles = src.walkTopDown().filter { !it.isDirectory }.toList()
            val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1)
            checkDiskSpace(totalBytes, File(targetPath))
                ?.let { emit(ProgressState(error = it)); return@flow }
            val totalFileCount = allFiles.size

            copyTree(src, dest, totalBytes, totalFileCount, 0L, -1, 0, progressScale = 90) { srcFile, written, progress, idx ->
                emit(ProgressState(
                    progress = progress,
                    totalBytes = totalBytes,
                    writtenBytes = written,
                    listSize = totalFileCount,
                    currentIndex = idx,
                    currentFileName = srcFile.name,
                    currentFileBytes = srcFile.length()
                ))
            }

            if (!src.deleteRecursively()) {
                emit(ProgressState(error = "원본 삭제 실패: ${src.name}"))
            } else {
                emit(ProgressState(progress = 100))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun unzipAction(zipFile: String, destPath: String, unzipHere: Boolean): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries().toList()
            val totalBytes = entries.sumOf { it.size }.coerceAtLeast(1)
            checkDiskSpace(totalBytes, File(destPath))
                ?.let { emit(ProgressState(error = it)); return@flow }
            val totalFileCount = entries.count { !it.isDirectory }
            val root = if (unzipHere) {
                File(destPath)
            } else {
                File(destPath, File(zipFile).nameWithoutExtension).also { it.mkdirs() }
            }
            var writtenBytes = 0L
            var prevProgress = -1
            var fileIndex = 0

            entries.forEach { entry ->
                val target = root.resolve(entry.name)
                if (!isUnderRoot(target, root)) return@forEach
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    fileIndex++
                    zf.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val (wb, pp) = pumpBytes(input, output, totalBytes, writtenBytes, prevProgress) { written, progress ->
                                emit(ProgressState(
                                    progress = progress,
                                    totalBytes = totalBytes,
                                    writtenBytes = written,
                                    listSize = totalFileCount,
                                    currentIndex = fileIndex,
                                    currentFileName = entry.name,
                                    currentFileBytes = entry.size
                                ))
                            }
                            writtenBytes = wb
                            prevProgress = pp
                        }
                    }
                }
            }
        }
        emit(ProgressState(progress = 100))
    }.flowOn(Dispatchers.IO)

    override fun makeDirectory(parentPath: String, name: String): Flow<ProgressState> = flow {
        val dir = File(parentPath, name)
        if (dir.mkdir()) {
            emit(ProgressState(progress = 100))
        } else {
            emit(ProgressState(error = "폴더 생성 실패: $name"))
        }
    }.flowOn(Dispatchers.IO)

    /** [dest] 파티션의 여유 공간이 [needed] 바이트 미만이면 오류 메시지를 반환하고, 충분하면 null을 반환한다. */
    private fun checkDiskSpace(needed: Long, dest: File): String? {
        val free = dest.freeSpace
        return if (free < needed)
            "디스크 공간 부족: 필요 ${needed / 1024 / 1024} MB, 여유 ${free / 1024 / 1024} MB"
        else null
    }

    /** [file]의 정규화 경로가 [root] 하위에 있는지 확인한다. Zip Slip 방지용. */
    private fun isUnderRoot(file: File, root: File): Boolean {
        val rootCanonical = root.canonicalPath + File.separator
        return file.canonicalFile.path.startsWith(rootCanonical)
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
        onProgress: suspend (src: File, written: Long, progress: Int, fileIndex: Int) -> Unit
    ): Triple<Long, Int, Int> {
        var wb = writtenBytes
        var pp = prevProgress
        var fi = fileIndex
        root.walkTopDown().onEnter { it != dest }.forEach { src ->
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
