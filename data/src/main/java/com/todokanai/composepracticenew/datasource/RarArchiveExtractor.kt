package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.datasource.LocalFileChecks.isUnderRoot
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

/** SevenZipJBinding으로 RAR 아카이브를 산정·추출하는 ArchiveExtractor. */
class RarArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".rar", ignoreCase = true)

    override fun measure(path: String): ArchiveSize =
        withArchive(path) { archive ->
            var bytes = 0L
            var count = 0
            for (i in 0 until archive.numberOfItems) {
                if (archive.getProperty(i, PropID.IS_FOLDER) as? Boolean == true) continue
                bytes += (archive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                count++
            }
            ArchiveSize(bytes, count)
        }

    override fun extract(path: String, root: File, tracker: ByteProgressTracker): Int =
        withArchive(path) { archive ->
            val callback = ExtractCallback(archive, root, tracker)
            archive.extract(null, false, callback)
            callback.skippedEntries
        }

    /** RAR 파일을 열어 [block]에 IInArchive를 넘기고 사용 후 닫는다. */
    private fun <T> withArchive(path: String, block: (IInArchive) -> T): T =
        RandomAccessFile(path, "r").use { raf ->
            SevenZip.openInArchive(null, RandomAccessFileInStream(raf)).use(block)
        }

    /**
     * 항목별 출력 스트림을 열고 기록하며 진행률을 보고하는 추출 콜백.
     * @param archive 추출 중인 아카이브
     * @param root 추출 대상 루트 디렉터리
     * @param tracker 누적 진행률 보고기
     */
    private class ExtractCallback(
        private val archive: IInArchive,
        private val root: File,
        private val tracker: ByteProgressTracker
    ) : IArchiveExtractCallback {
        /** 경로 검증 실패·쓰기 실패·추출 오류로 건너뛴 항목 수. */
        var skippedEntries = 0
            private set
        private var currentOutputStream: OutputStream? = null
        private var currentNormalizedName = ""
        private var currentItemSize = 0L
        private var skipCurrent = false

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            skipCurrent = false
            if (extractAskMode != ExtractAskMode.EXTRACT) return null
            if (archive.getProperty(index, PropID.IS_FOLDER) as? Boolean == true) return null
            val rawName = archive.getProperty(index, PropID.PATH) as? String ?: return null
            currentNormalizedName = rawName.replace('\\', '/')
            currentItemSize = (archive.getProperty(index, PropID.SIZE) as? Long) ?: 0L
            val target = root.resolve(currentNormalizedName)
            if (!isUnderRoot(target, root)) { skipCurrent = true; skippedEntries++; return null }
            target.parentFile?.mkdirs()
            tracker.startFile()
            return try {
                val out = target.outputStream()
                currentOutputStream = out
                ISequentialOutStream { data ->
                    out.write(data)
                    tracker.addBytes(data.size.toLong(), currentNormalizedName, currentItemSize, clampTo100 = true)
                    data.size
                }
            } catch (e: Exception) { skipCurrent = true; skippedEntries++; null }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) {}

        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            currentOutputStream?.close()
            currentOutputStream = null
            if (!skipCurrent && extractOperationResult != ExtractOperationResult.OK) skippedEntries++
        }

        override fun setTotal(total: Long) {}
        override fun setCompleted(complete: Long) {}
    }
}
