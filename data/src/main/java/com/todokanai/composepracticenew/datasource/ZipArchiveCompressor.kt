package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** java.util.zip으로 ZIP 아카이브를 쓰는 ArchiveCompressor. 폴더는 `이름/` 항목으로 담아, 빈 폴더도 압축 해제 시 그대로 복원된다. */
class ZipArchiveCompressor @Inject constructor() : ArchiveCompressor {

    override fun write(entries: List<ArchiveSource>, output: File, tracker: ByteProgressTracker) {
        ZipOutputStream(output.outputStream()).use { zos ->
            entries.forEach { entry ->
                when (entry) {
                    is ArchiveSource.Folder -> writeFolder(zos, entry)
                    is ArchiveSource.Regular -> writeFile(zos, entry, tracker)
                }
            }
        }
    }

    /**
     * 폴더 항목을 내용 없는 `이름/` 항목으로 쓴다.
     * @param zos 쓰는 중인 ZIP 출력
     * @param entry 쓸 폴더 항목
     */
    private fun writeFolder(zos: ZipOutputStream, entry: ArchiveSource.Folder) {
        zos.putNextEntry(ZipEntry("${entry.name}/"))
        zos.closeEntry()
    }

    /**
     * 파일 항목의 내용을 쓰며 [tracker]로 진행을 보고한다.
     * @param zos 쓰는 중인 ZIP 출력
     * @param entry 쓸 파일 항목
     * @param tracker 누적 진행률 보고기
     */
    private fun writeFile(zos: ZipOutputStream, entry: ArchiveSource.Regular, tracker: ByteProgressTracker) {
        tracker.startFile()
        zos.putNextEntry(ZipEntry(entry.name))
        entry.file.inputStream().use { input ->
            tracker.pump(input, zos, entry.file.name, entry.bytes)
        }
        zos.closeEntry()
        tracker.onFileCompleted(entry.file.name, entry.bytes)
    }
}
