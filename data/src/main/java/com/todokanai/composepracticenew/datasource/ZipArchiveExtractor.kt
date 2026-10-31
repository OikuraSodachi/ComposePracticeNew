package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.isUnderRoot
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/** java.util.zip으로 ZIP 아카이브를 산정·추출하는 ArchiveExtractor. */
class ZipArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".zip", ignoreCase = true)

    override fun measure(path: String): ArchiveSize =
        ZipFile(path).use { zf ->
            val entries = zf.entries().toList()
            ArchiveSize(bytes = entries.sumOf { it.size.coerceAtLeast(0) }, fileCount = entries.count { !it.isDirectory })
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
                            tracker.pump(input, output, entry.name, entry.size)
                        }
                    }
                }
            }
        }
        return skipped
    }
}
