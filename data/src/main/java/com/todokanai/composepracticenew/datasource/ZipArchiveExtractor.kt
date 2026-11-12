package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.util.zip.CRC32
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

    override fun extract(path: String, writer: ArchiveEntryWriter, tracker: ByteProgressTracker) {
        ZipFile(path).use { zf ->
            entriesOf(zf).forEach { entry ->
                if (entry.isDirectory) {
                    writer.createFolder(entry.name)
                    return@forEach
                }
                VerifyingZipEntryInputStream(zf.getInputStream(entry), entry).use { input ->
                    val output = writer.openFile(entry.name) ?: return@forEach
                    tracker.startFile()
                    try {
                        output.use { tracker.pump(input, it, entry.name, knownSizeOf(entry)) }
                    } catch (e: CancellationException) {
                        writer.discardFile(entry.name)
                        throw e
                    }
                }
                tracker.onFileCompleted(entry.name, knownSizeOf(entry))
            }
        }
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

/**
 * 항목 내용을 읽으며 CRC32와 길이를 누적하고, 끝에 닿으면 central directory에 적힌 값과 비교하는 입력 스트림.
 * java.util.zip.ZipFile의 입력 스트림은 CRC를 검증하지 않으므로(검증은 ZipInputStream만 한다), 손상된 항목을 성공으로 풀지 않도록 여기서 검증한다.
 * 끝에서 던지므로 마지막 바이트까지 기록한 뒤 실패하며, 호출부는 그 항목을 완료로 확정하지 않는다.
 * @param input 항목의 압축 해제된 내용
 * @param entry 기대 CRC·크기를 담은 central directory 항목
 */
internal class VerifyingZipEntryInputStream(
    private val input: InputStream,
    private val entry: ZipEntry,
) : InputStream() {

    /** 지금까지 읽은 내용의 CRC32. */
    private val crc = CRC32()
    /** 지금까지 읽은 바이트 수. */
    private var readBytes = 0L
    /** 끝에서 검증을 마쳤는지. 끝에 닿은 뒤 다시 읽어도 한 번만 검증한다. */
    private var verified = false

    override fun read(): Int {
        val b = input.read()
        if (b == -1) verifyAtEnd() else { crc.update(b); readBytes++ }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = input.read(b, off, len)
        if (n == -1) verifyAtEnd() else { crc.update(b, off, n); readBytes += n }
        return n
    }

    override fun available(): Int = input.available()

    override fun close() = input.close()

    /** 끝에 닿았을 때 읽은 내용이 항목의 CRC·크기와 맞는지 확인한다. CRC를 모르면 검증할 수 없으므로 같은 실패로 알린다. */
    private fun verifyAtEnd() {
        if (verified) return
        verified = true
        val reason = when {
            entry.crc == -1L -> "CRC 없음"
            entry.crc != crc.value -> "CRC"
            entry.size >= 0 && entry.size != readBytes -> "크기"
            else -> return
        }
        throw ZipException("풀 수 없는 항목입니다: ${entry.name} ($reason)")
    }
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
