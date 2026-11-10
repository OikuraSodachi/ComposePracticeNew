package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.entryTargetOf
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

/** SevenZipJBinding으로 RAR 아카이브를 산정·추출하는 ArchiveExtractor. 이름이 `.rar`이고 내용도 RAR(RAR4·RAR5)인 파일만 다룬다. */
class RarArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".rar", ignoreCase = true)

    override fun measure(path: String): ArchiveSize =
        withArchive(path) { archive ->
            var knownBytes = 0L
            var count = 0
            var unknownSizeCount = 0
            for (i in 0 until archive.numberOfItems) {
                if (archive.getProperty(i, PropID.IS_FOLDER) as? Boolean == true) continue
                val size = knownSizeOf(archive, i)
                if (size == null) unknownSizeCount++ else knownBytes += size
                count++
            }
            ArchiveSize(knownBytes, count, unknownSizeCount)
        }

    // 폴더 항목은 extract가 만들지 않으므로(getStream이 건너뜀) 빼고, 경로가 없는 항목도 extract가 건너뛰므로 뺀다
    override fun entryNames(path: String): List<String> =
        withArchive(path) { archive ->
            (0 until archive.numberOfItems)
                .filterNot { archive.getProperty(it, PropID.IS_FOLDER) as? Boolean == true }
                .mapNotNull { normalizedPathOf(archive, it) }
        }

    override fun extract(path: String, root: File, tracker: ByteProgressTracker): Int =
        withArchive(path) { archive ->
            val callback = ExtractCallback(archive, root, tracker)
            archive.extract(null, false, callback)
            callback.skippedEntries
        }

    /** RAR 파일을 열어 [block]에 IInArchive를 넘기고 사용 후 닫는다. 7-Zip은 내용으로 형식을 판별해 다른 형식도 열어 주므로, RAR이 아니면 IOException으로 실패시킨다. */
    private fun <T> withArchive(path: String, block: (IInArchive) -> T): T =
        RandomAccessFile(path, "r").use { raf ->
            SevenZip.openInArchive(null, RandomAccessFileInStream(raf)).use { archive ->
                if (!isRar(archive.archiveFormat)) throw IOException("RAR 파일이 아닙니다: ${File(path).name}")
                block(archive)
            }
        }

    /** [format]이 이 추출기가 다루는 RAR 형식(RAR4·RAR5)인지 판별한다. */
    private fun isRar(format: ArchiveFormat): Boolean = format == ArchiveFormat.RAR || format == ArchiveFormat.RAR5

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
        /** 경로 없음·경로 검증 실패·쓰기 실패·추출 오류로 건너뛴 항목 수. */
        var skippedEntries = 0
            private set
        /** 현재 항목을 쓰고 있는 출력 스트림. 항목 완료 시 닫고 null로 되돌린다. */
        private var currentOutputStream: OutputStream? = null
        /** 현재 항목의 '/' 구분 정규화 경로. */
        private var currentNormalizedName = ""
        /** 현재 항목의 압축 해제 후 크기(bytes). 아카이브가 알려주지 않으면 null. */
        private var currentItemSize: Long? = null
        /** 현재 항목을 건너뛰었는지 여부 — 이미 집계한 항목을 결과 단계에서 중복 집계하지 않기 위해 쓴다. */
        private var skipCurrent = false

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            skipCurrent = false
            if (extractAskMode != ExtractAskMode.EXTRACT) return null
            if (archive.getProperty(index, PropID.IS_FOLDER) as? Boolean == true) return null
            currentNormalizedName = normalizedPathOf(archive, index) ?: return skipEntry()
            currentItemSize = knownSizeOf(archive, index)
            val target = entryTargetOf(root, currentNormalizedName) ?: return skipEntry()
            target.parentFile?.mkdirs()
            tracker.startFile()
            return try {
                val out = target.outputStream()
                currentOutputStream = out
                ISequentialOutStream { data ->
                    out.write(data)
                    tracker.addBytes(data.size.toLong(), currentNormalizedName, currentItemSize)
                    data.size
                }
            } catch (e: Exception) { skipEntry() }
        }

        /** 현재 항목을 건너뜀으로 집계하고, 라이브러리에 건너뛰라고 알리는 null을 돌려준다. */
        private fun skipEntry(): ISequentialOutStream? {
            skipCurrent = true
            skippedEntries++
            return null
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) {}

        /** 결과는 마지막 [getStream] 항목의 것이다(SevenZipJBinding 계약). 출력을 연 항목이 OK로 끝나야 그 몫을 확정한다 — 폴더·건너뛴 항목은 출력을 열지 않는다. */
        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            val wroteEntry = currentOutputStream != null
            currentOutputStream?.close()
            currentOutputStream = null
            if (extractOperationResult == ExtractOperationResult.OK) {
                if (wroteEntry) tracker.onFileCompleted(currentNormalizedName, currentItemSize)
            } else if (!skipCurrent) {
                skippedEntries++
            }
        }

        override fun setTotal(total: Long) {}
        override fun setCompleted(complete: Long) {}
    }
}

/**
 * 항목의 압축 해제 후 크기를 구한다. 산정과 추출이 같은 기준으로 크기를 모르는 항목을 가르도록 한 곳에 둔다.
 * @param archive 열린 아카이브
 * @param index 항목 번호
 * @return 크기(bytes), 아카이브가 알려주지 않으면 null
 */
private fun knownSizeOf(archive: IInArchive, index: Int): Long? = (archive.getProperty(index, PropID.SIZE) as? Long)?.takeIf { it >= 0 }

/**
 * 항목의 아카이브 안 경로를 '/' 구분으로 정규화해 구한다. 나열과 추출이 같은 이름으로 항목을 가리키도록 한 곳에 둔다.
 * @param archive 열린 아카이브
 * @param index 항목 번호
 * @return '/' 구분 경로, 아카이브가 경로를 알려주지 않으면 null
 */
private fun normalizedPathOf(archive: IInArchive, index: Int): String? = (archive.getProperty(index, PropID.PATH) as? String)?.replace('\\', '/')
