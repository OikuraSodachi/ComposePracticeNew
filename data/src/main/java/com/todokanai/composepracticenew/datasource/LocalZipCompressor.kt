package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** 로컬 파일·디렉터리를 하나의 ZIP 파일로 압축한다. */
class LocalZipCompressor @Inject constructor() {

    /**
     * [targetFiles]를 [zipFile]로 압축한다.
     * @param targetFiles 압축 대상 파일 경로 집합
     * @param zipFile 생성할 ZIP 파일의 절대 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun zip(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        val roots = targetFiles.map(::File)
        var totalBytesAcc = 0L
        val allFiles = roots.flatMap { root ->
            root.walkTopDown().filter { f -> !f.isDirectory }
                .onEach { totalBytesAcc += it.length() }
                .map { root to it }
                .toList()
        }
        if (allFiles.isEmpty()) throw Exception("압축할 파일이 없습니다.")
        val totalBytes = totalBytesAcc.coerceAtLeast(1)
        checkDiskSpace(totalBytes, File(zipFile).parentFile ?: File(zipFile))
            ?.let { throw Exception(it) }
        onProgress(ProgressState(progress = 0))
        val tracker = ByteProgressTracker(totalBytes, allFiles.size, onProgress)

        ZipOutputStream(File(zipFile).outputStream()).use { zos ->
            allFiles.forEach { (root, sFile) ->
                tracker.startFile()
                val entryName = root.toPath().relativize(sFile.toPath())
                    .toString().replace("\\", "/")
                zos.putNextEntry(ZipEntry(entryName))
                sFile.inputStream().use { input ->
                    tracker.pump(input, zos, sFile.name, sFile.length())
                }
                zos.closeEntry()
            }
        }
        tracker.reportComplete()
    }
}
