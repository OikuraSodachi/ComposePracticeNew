package com.todokanai.composepracticenew.tools.fileaction

import com.todokanai.composepracticenew.model.ProgressState
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** ViewModel 이상 단계에서 보이면 안됨 */
class UnzipAction(val onSpaceRequired: () -> Unit) {

    fun unzip(
        zipFile: ZipFile,
        target: File,
        progressCallback: (progress: ProgressState) -> Unit,
        unzipHere: Boolean
    ) {
        if (unzipHere) {
            unzipHere_Wrapper(zipFile, target, progressCallback)
        } else {
            unzip_Wrapper(zipFile, target, progressCallback)
        }
    }

    private fun unzipHere_Wrapper(
        zipFile: ZipFile,
        target: File,
        progressCallback: (progress: ProgressState) -> Unit,
    ) {
        if (zipFile.entries().toList().sumOf { it.size } >= target.freeSpace) {
            onSpaceRequired()
        } else {
            unzipHere_td(zipFile, target, progressCallback)
        }
    }

    private fun unzip_Wrapper(
        zipFile: ZipFile,
        target: File,
        progressCallback: (progress: ProgressState) -> Unit,
    ) {
        if (zipFile.entries().toList().sumOf { it.size } >= target.freeSpace) {
            onSpaceRequired()
        } else {
            unzip_td(zipFile, target, progressCallback)
        }
    }

    private fun unzipHere_td(zipFile: ZipFile, target: File, callback: (progress: ProgressState) -> Unit) {
        val buffer = ByteArray(1024)
        val totalBytes = zipFile.entries().toList().sumOf { it.size }
        var processedBytes = 0L
        var prevProgress = 0
        val entries = zipFile.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement() as ZipEntry
            val destFilePath = target.resolve(entry.name)

            if (entry.isDirectory) {
                Files.createDirectories(destFilePath.toPath())
            } else {
                val parentDir = destFilePath.parentFile
                if (!parentDir.exists()) parentDir.mkdirs()

                zipFile.getInputStream(entry).use { inputStream ->
                    FileOutputStream(destFilePath).use { outputStream ->
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            processedBytes += bytesRead.toLong()
                            val progress = (100 * processedBytes / totalBytes).toInt()
                            if (prevProgress != progress || processedBytes == totalBytes) {
                                callback(ProgressState(progress))
                                prevProgress = progress
                            }
                        }
                    }
                }
            }
        }
    }

    private fun unzip_td(zipFile: ZipFile, target: File, callback: (progress: ProgressState) -> Unit) {
        val folder = File("${target.toPath()}/${File(zipFile.name).nameWithoutExtension}")
        if (!folder.exists()) folder.mkdir()
        unzipHere_td(zipFile, folder, callback)
    }
}
