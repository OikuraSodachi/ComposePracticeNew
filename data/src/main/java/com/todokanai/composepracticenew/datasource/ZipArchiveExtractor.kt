package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject

/** java.util.zip으로 ZIP 아카이브를 산정·추출하는 ArchiveExtractor. 이름이 `.zip`인 파일만 다루며, 속이 ZIP인 다른 형식(.apk, .jar, .docx 등)은 지원 범위 밖이다. */
class ZipArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".zip", ignoreCase = true)

    override fun measure(path: String): ArchiveSize =
        ZipFile(path).use { zf ->
            val files = entriesOf(zf).filter { !it.isDirectory }
            val knownSizes = files.mapNotNull(::knownSizeOf)
            ArchiveSize(knownBytes = knownSizes.sum(), fileCount = files.size, unknownSizeCount = files.size - knownSizes.size)
        }

    // 폴더 항목도 extract가 만들므로 함께 담는다
    override fun entryNames(path: String): List<String> = ZipFile(path).use { zf -> entriesOf(zf).map { it.name } }

    override fun extract(path: String, root: ExtractionRoot, tracker: ByteProgressTracker): Int {
        var skipped = 0
        ZipFile(path).use { zf ->
            entriesOf(zf).forEach { entry ->
                val target = root.targetOf(entry.name) ?: run { skipped++; return@forEach }
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
     * 아카이브의 항목을 모두 읽는다. [measure]·[entryNames]·[extract]가 항목 정보를 해석하지 못한 아카이브를 같은 실패로 알리도록 한 곳에 둔다.
     * @param zf 연 ZIP 파일
     * @return 아카이브에 담긴 순서의 항목 목록
     * @throws ZipException 항목 이름이나 설명을 해석하지 못했을 때
     */
    private fun entriesOf(zf: ZipFile): List<ZipEntry> = decodingZipEntries { zf.entries().toList() }

    /**
     * 항목의 압축 해제 후 크기를 구한다. [measure]와 [extract]가 같은 기준으로 크기를 모르는 항목을 가르도록 한 곳에 둔다.
     * @param entry 아카이브 항목
     * @return 크기(bytes), 아카이브가 알려주지 않으면(-1) null
     */
    private fun knownSizeOf(entry: ZipEntry): Long? = entry.size.takeIf { it >= 0 }
}

/** 항목 정보를 해석하지 못한 아카이브의 실패 메시지. */
private const val UNDECODABLE_ENTRY_MESSAGE = "항목 이름을 해석할 수 없는 압축 파일입니다(UTF-8이 아닌 이름 등)"

/**
 * [read]가 항목 이름·설명을 해석하지 못해 던지는 IllegalArgumentException을 ZipException으로 바꾼다.
 * 구형 java.util.zip(Android 13 이하)은 UTF-8이 아닌 이름을 연 뒤 항목을 읽을 때 IAE로 알리고, 신형은 열 때 ZipException으로 알린다.
 * 어느 쪽이든 읽지 못하는 아카이브를 IOException으로 알리는 [ArchiveExtractor] 계약을 지키도록 여기서 맞춘다.
 * 이 프로젝트 코드의 IAE(구현 오류)까지 실패 결과로 바꾸지 않도록 항목을 읽는 호출만 감싼다.
 * @param read 항목을 읽는 호출
 * @return [read]의 결과
 * @throws ZipException [read]가 IllegalArgumentException을 던졌을 때(원인으로 담음)
 */
internal fun <T> decodingZipEntries(read: () -> T): T =
    try {
        read()
    } catch (e: IllegalArgumentException) {
        throw ZipException(UNDECODABLE_ENTRY_MESSAGE).apply { initCause(e) }
    }
