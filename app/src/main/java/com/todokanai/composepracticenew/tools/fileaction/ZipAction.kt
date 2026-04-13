package com.todokanai.composepracticenew.tools.fileaction

import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.tools.independent.getTotalSize_td
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ViewModel 이상 단계에서 보이면 안됨 */
class ZipAction(val onSpaceRequired: () -> Unit) {

    /** 여유공간 체크할때 단순 files의 크기 합을 기준으로 체크함 */
    fun zipFiles(
        files :Array<File>,
        zipFile : File,
        progressCallback : (progress:ProgressState)->Unit,
    ) {
        fun zip_temp(file: File, zipFile: File, callback: (progress: ProgressState) -> Unit) {

            val totalBytesToCompress = getTotalSize_td(arrayOf(file))
            var compressedBytes = 0L
            var prevProgress = 0

            // 파일을 zip으로 압축
            zipFile.parentFile.mkdirs()
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                file.walkTopDown().forEach { sFile ->
                    if (!sFile.isDirectory) {
                        val entryName = file.toPath().relativize(sFile.toPath()).toString().replace("\\", "/")
                        val zipEntry = ZipEntry(entryName)
                        zos.putNextEntry(zipEntry)

                        // 버퍼를 활용하여 파일 읽기 및 쓰기
                        val buffer = ByteArray(1024)
                        sFile.inputStream().use { input ->
                            var bytesRead = input.read(buffer)
                            while (bytesRead != -1) {
                                zos.write(buffer, 0, bytesRead)
                                compressedBytes += bytesRead.toLong()



                                val progress = (compressedBytes * 100 / totalBytesToCompress).toInt()
                                if(prevProgress!=progress || compressedBytes==totalBytesToCompress) {

                                    val state = ProgressState(progress = progress)
                                    callback(state)
                                    //callback(progress)
                                    prevProgress = progress
                                }
                               // println("compressedBytes: $compressedBytes, progress: $progress")
                                bytesRead = input.read(buffer)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }

        if(getTotalSize_td(files)>=zipFile.parentFile.freeSpace){
            onSpaceRequired()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                files.forEach { file ->
                    zip_temp(file, zipFile, progressCallback)
                }
            }
        }
    }

    /*


    /** test */
     fun compressToZip(
        files: List<File>,
        zipFile: File,
        progressCallback : (progress:Int)->Unit,
      //  onComplete : ()->Unit,
        onSpaceRequired : ()->Unit
    ) {

        val buffer = ByteArray(1024)
        val out = ZipOutputStream(FileOutputStream(zipFile))

        files.forEach { file ->
            val path = if (file.isDirectory) "${file.name}/" else file.name
            val entry = ZipEntry(path)
            out.putNextEntry(entry)

            if (!file.isDirectory) {
                val inputStream = FileInputStream(file)
                var len: Int
                while (inputStream.read(buffer).also { len = it } > 0) {
                    out.write(buffer, 0, len)
                }
                inputStream.close()
            }
            out.closeEntry()
        }
        out.close()
    }

     */
}