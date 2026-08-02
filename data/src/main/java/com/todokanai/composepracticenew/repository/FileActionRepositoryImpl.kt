package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ProgressState
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
        var writtenBytes = 0L

        ZipOutputStream(File(zipFile).outputStream()).use { zos ->
            targetFiles.map(::File).forEach { root ->
                root.walkTopDown().filter { !it.isDirectory }.forEach { sFile ->
                    val entryName = root.toPath().relativize(sFile.toPath())
                        .toString().replace("\\", "/")
                    zos.putNextEntry(ZipEntry(entryName))
                    val buffer = ByteArray(8192)
                    sFile.inputStream().use { input ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            zos.write(buffer, 0, read)
                            writtenBytes += read
                            emit(ProgressState(progress = (writtenBytes * 100 / totalBytes).toInt()))
                            read = input.read(buffer)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun copyAction(targetFiles: List<String>, targetPath: String): Flow<ProgressState> = flow {
        val total = targetFiles.size.coerceAtLeast(1)
        targetFiles.forEachIndexed { index, path ->
            val src = File(path)
            // 주의: targetPath 가 src 의 부모 디렉터리와 동일한 경우
            // File(targetPath, src.name) == src 가 성립하여, overwrite=true 로 인해
            // copyRecursively 가 src 를 먼저 삭제한 뒤 복사를 시도 → kotlin.io.NoSuchFileException 발생.
            // 출발지-목적지 동일 경로 검사는 호출부에서 처리할 것.
            src.copyRecursively(File(targetPath, src.name), overwrite = true)
            emit(ProgressState(progress = (index + 1) * 100 / total))
        }
    }.flowOn(Dispatchers.IO)

    override fun renameFile(targetFile: String, newName: String): Flow<ProgressState> = flow {
        val file = File(targetFile)
        file.renameTo(File("${file.parent}/$newName"))
        emit(ProgressState(progress = 100))
    }.flowOn(Dispatchers.IO)

    override fun deleteFile(targetFile: String): Flow<ProgressState> = flow {
        val files = File(targetFile).walkTopDown().toList()
        val total = files.size.coerceAtLeast(1)
        files.forEachIndexed { index, file ->
            file.delete()
            emit(ProgressState(progress = (index + 1) * 100 / total))
        }
    }.flowOn(Dispatchers.IO)

    override fun moveFile(targetFile: String, targetPath: String): Flow<ProgressState> = flow {
        val src = File(targetFile)
        src.copyRecursively(File(targetPath, src.name), overwrite = true)
        emit(ProgressState(progress = 50))
        src.deleteRecursively()
        emit(ProgressState(progress = 100))
    }.flowOn(Dispatchers.IO)
}
