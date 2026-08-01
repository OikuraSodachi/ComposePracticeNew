package com.todokanai.composepracticenew.tools.fileaction

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.tools.independent.getTotalSize_td
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ViewModel 이상 단계에서 보이면 안됨 */
class ZipAction(val onSpaceRequired: () -> Unit) {

    fun zipFiles(
        files: Array<File>,
        zipFile: File,
        progressCallback: (progress: ProgressState) -> Unit,
    ) {
        fun zip_temp(file: File, zipFile: File, callback: (progress: ProgressState) -> Unit) {
            val totalBytesToCompress = getTotalSize_td(arrayOf(file))
            var compressedBytes = 0L
            var prevProgress = 0

            zipFile.parentFile.mkdirs()
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                file.walkTopDown().forEach { sFile ->
                    if (!sFile.isDirectory) {
                        val entryName = file.toPath().relativize(sFile.toPath()).toString().replace("\\", "/")
                        val zipEntry = ZipEntry(entryName)
                        zos.putNextEntry(zipEntry)

                        val buffer = ByteArray(1024)
                        sFile.inputStream().use { input ->
                            var bytesRead = input.read(buffer)
                            while (bytesRead != -1) {
                                zos.write(buffer, 0, bytesRead)
                                compressedBytes += bytesRead.toLong()
                                val progress = (compressedBytes * 100 / totalBytesToCompress).toInt()
                                if (prevProgress != progress || compressedBytes == totalBytesToCompress) {
                                    callback(ProgressState(progress = progress))
                                    prevProgress = progress
                                }
                                bytesRead = input.read(buffer)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }

        if (getTotalSize_td(files) >= zipFile.parentFile.freeSpace) {
            onSpaceRequired()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                files.forEach { file ->
                    zip_temp(file, zipFile, progressCallback)
                }
            }
        }
    }
}
