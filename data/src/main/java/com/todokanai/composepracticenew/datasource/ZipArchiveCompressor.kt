package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ArchiveFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** java.util.zip으로 ZIP 아카이브를 쓰는 ArchiveCompressor. 폴더는 `이름/` 항목으로 담아, 빈 폴더도 압축 해제 시 그대로 복원된다. */
class ZipArchiveCompressor @Inject constructor() : ArchiveCompressor {

    override val format: ArchiveFormat = ArchiveFormat.ZIP

    override fun write(entries: List<ArchiveSource>, output: File, tracker: ByteProgressTracker) {
        ZipOutputStream(output.outputStream()).use { zos ->
            entries.forEach { (name, file) ->
                if (file.isDirectory) {
                    zos.putNextEntry(ZipEntry("$name/"))
                    zos.closeEntry()
                    return@forEach
                }
                tracker.startFile()
                zos.putNextEntry(ZipEntry(name))
                file.inputStream().use { input ->
                    tracker.pump(input, zos, file.name, file.length())
                }
                zos.closeEntry()
                tracker.onFileCompleted(file.name, file.length())
            }
        }
    }
}
