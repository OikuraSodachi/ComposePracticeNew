package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Implements file mutations (zip, copy, rename, delete, move) directly via Java I/O. */
@Singleton
class FileActionRepositoryImpl @Inject constructor() : FileActionRepository {

    override fun zipAction(targetFiles: List<String>, zipFile: String): Flow<ProgressState> = flow {
        val allFiles = targetFiles.map(::File).flatMap { it.walkTopDown().filter { f -> !f.isDirectory }.toList() }
        val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1)
        val totalFileCount = allFiles.size
        var writtenBytes = 0L
        var fileIndex = 0

        ZipOutputStream(File(zipFile).outputStream()).use { zos ->
            targetFiles.map(::File).forEach { root ->
                root.walkTopDown().filter { !it.isDirectory }.forEach { sFile ->
                    fileIndex++
                    val entryName = root.toPath().relativize(sFile.toPath())
                        .toString().replace("\\", "/")
                    zos.putNextEntry(ZipEntry(entryName))
                    val buffer = ByteArray(8192)
                    sFile.inputStream().use { input ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            zos.write(buffer, 0, read)
                            writtenBytes += read
                            emit(ProgressState(
                                progress = (writtenBytes * 100 / totalBytes).toInt(),
                                totalSize = readableFileSize_td(totalBytes),
                                currentSize = readableFileSize_td(writtenBytes),
                                listSize = totalFileCount,
                                currentIndex = fileIndex,
                                currentFileName = sFile.name,
                                currentFileSize = readableFileSize_td(sFile.length())
                            ))
                            read = input.read(buffer)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun copyAction(targetFiles: List<String>, targetPath: String): Flow<ProgressState> = flow {
        emit(ProgressState(progress = 0))
        val roots = targetFiles.map(::File)
        val allFiles = roots.flatMap { it.walkTopDown().filter { f -> !f.isDirectory }.toList() }
        val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1)
        val totalFileCount = allFiles.size
        var writtenBytes = 0L
        var fileIndex = 0

        roots.forEach { root ->
            val dest = File(targetPath, root.name)
            root.walkTopDown().forEach { src ->
                val target = dest.toPath().resolve(root.toPath().relativize(src.toPath())).toFile()
                if (src.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    fileIndex++
                    val buffer = ByteArray(8192)
                    src.inputStream().use { input ->
                        target.outputStream().use { output ->
                            var read = input.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                writtenBytes += read
                                emit(ProgressState(
                                    progress = (writtenBytes * 100 / totalBytes).toInt(),
                                    totalSize = readableFileSize_td(totalBytes),
                                    currentSize = readableFileSize_td(writtenBytes),
                                    listSize = totalFileCount,
                                    currentIndex = fileIndex,
                                    currentFileName = src.name,
                                    currentFileSize = readableFileSize_td(src.length())
                                ))
                                read = input.read(buffer)
                            }
                        }
                    }
                }
            }
        }
        emit(ProgressState(
            progress = 100,
            totalSize = readableFileSize_td(totalBytes),
            currentSize = readableFileSize_td(totalBytes),
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
        val allFiles = src.walkTopDown().filter { !it.isDirectory }.toList()
        val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1)
        val totalFileCount = allFiles.size
        var writtenBytes = 0L
        var fileIndex = 0
        val dest = File(targetPath, src.name)

        src.walkTopDown().forEach { srcFile ->
            val target = dest.toPath().resolve(src.toPath().relativize(srcFile.toPath())).toFile()
            if (srcFile.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                fileIndex++
                val buffer = ByteArray(8192)
                srcFile.inputStream().use { input ->
                    target.outputStream().use { output ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            writtenBytes += read
                            // copy phase: 0–90%
                            emit(ProgressState(
                                progress = (writtenBytes * 90 / totalBytes).toInt(),
                                totalSize = readableFileSize_td(totalBytes),
                                currentSize = readableFileSize_td(writtenBytes),
                                listSize = totalFileCount,
                                currentIndex = fileIndex,
                                currentFileName = srcFile.name,
                                currentFileSize = readableFileSize_td(srcFile.length())
                            ))
                            read = input.read(buffer)
                        }
                    }
                }
            }
        }

        src.deleteRecursively()
        emit(ProgressState(progress = 100))
    }.flowOn(Dispatchers.IO)
}
