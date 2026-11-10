package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
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
                if (entryKindOf(archive, i) == RarEntryKind.Folder) continue
                val size = knownSizeOf(archive, i)
                if (size == null) unknownSizeCount++ else knownBytes += size
                count++
            }
            ArchiveSize(knownBytes, count, unknownSizeCount)
        }

    // extract가 만드는 항목(Extracted)만 담는다 — 판별은 getStream과 같은 entryKindOf로 한다
    override fun entryNames(path: String): List<String> =
        withArchive(path) { archive ->
            (0 until archive.numberOfItems).mapNotNull { (entryKindOf(archive, it) as? RarEntryKind.Extracted)?.path }
        }

    override fun extract(path: String, root: ExtractionRoot, tracker: ByteProgressTracker): Int =
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
        private val root: ExtractionRoot,
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
            currentNormalizedName = when (val kind = entryKindOf(archive, index)) {
                RarEntryKind.Folder -> return null
                RarEntryKind.Unnamed -> return skipEntry()
                is RarEntryKind.Extracted -> kind.path
            }
            currentItemSize = knownSizeOf(archive, index)
            val target = root.targetOf(currentNormalizedName) ?: return skipEntry()
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
 * 열린 아카이브의 항목을 추출이 어떻게 다루는지 판별한다.
 * @param archive 열린 아카이브
 * @param index 항목 번호
 * @return 항목 종류
 */
private fun entryKindOf(archive: IInArchive, index: Int): RarEntryKind =
    rarEntryKindOf(archive.getProperty(index, PropID.IS_FOLDER), archive.getProperty(index, PropID.PATH))

/**
 * 항목 속성으로 추출이 그 항목을 어떻게 다루는지 판별한다. 산정·나열·추출이 같은 규칙으로 항목을 고르도록 한 곳에 둔다.
 * 라이브러리 없이 검증할 수 있도록 속성 값만 받는다.
 * @param isFolder 항목의 IS_FOLDER 속성 값
 * @param path 항목의 PATH 속성 값
 * @return 폴더면 [RarEntryKind.Folder], 경로가 없으면 [RarEntryKind.Unnamed], 그 밖에는 '/' 구분으로 정규화한 경로의 [RarEntryKind.Extracted]
 */
internal fun rarEntryKindOf(isFolder: Any?, path: Any?): RarEntryKind = when {
    isFolder as? Boolean == true -> RarEntryKind.Folder
    path !is String -> RarEntryKind.Unnamed
    else -> RarEntryKind.Extracted(path.replace('\\', '/'))
}

/** 추출이 RAR 항목 하나를 어떻게 다루는지 나타내는 종류. */
internal sealed interface RarEntryKind {

    /** 폴더 항목 — 추출이 만들지 않고 건너뜀으로도 세지 않는다. */
    data object Folder : RarEntryKind

    /** 경로가 없는 항목 — 추출이 건너뛰고 건너뜀으로 센다. */
    data object Unnamed : RarEntryKind

    /**
     * 추출이 파일로 만드는 항목.
     * @param path 아카이브 안의 '/' 구분 경로
     */
    data class Extracted(val path: String) : RarEntryKind
}
