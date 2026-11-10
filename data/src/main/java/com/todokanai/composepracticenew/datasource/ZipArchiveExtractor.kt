package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.isUnderRoot
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

/** java.util.zip으로 ZIP 아카이브를 산정·추출하는 ArchiveExtractor. 이름이 `.zip`인 파일만 다루며, 속이 ZIP인 다른 형식(.apk, .jar, .docx 등)은 지원 범위 밖이다. */
class ZipArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".zip", ignoreCase = true)

    override fun measure(path: String): ArchiveSize =
        ZipFile(path).use { zf ->
            val files = zf.entries().toList().filter { !it.isDirectory }
            val knownSizes = files.mapNotNull(::knownSizeOf)
            ArchiveSize(knownBytes = knownSizes.sum(), fileCount = files.size, unknownSizeCount = files.size - knownSizes.size)
        }

    override fun extract(path: String, root: File, tracker: ByteProgressTracker): Int {
        var skipped = 0
        ZipFile(path).use { zf ->
            zf.entries().toList().forEach { entry ->
                val target = root.resolve(entry.name)
                if (!isUnderRoot(target, root)) { skipped++; return@forEach }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    tracker.startFile()
                    zf.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            tracker.pump(input, output, entry.name, knownSizeOf(entry))
                        }
                    }
                    tracker.onFileCompleted(entry.name, knownSizeOf(entry))
                }
            }
        }
        return skipped
    }

    /**
     * 항목의 압축 해제 후 크기를 구한다. [measure]와 [extract]가 같은 기준으로 크기를 모르는 항목을 가르도록 한 곳에 둔다.
     * @param entry 아카이브 항목
     * @return 크기(bytes), 아카이브가 알려주지 않으면(-1) null
     */
    private fun knownSizeOf(entry: ZipEntry): Long? = entry.size.takeIf { it >= 0 }
}
