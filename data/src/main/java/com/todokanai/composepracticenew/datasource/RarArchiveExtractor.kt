package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import kotlinx.coroutines.CancellationException
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * SevenZipJBinding으로 RAR 아카이브를 산정·추출하는 ArchiveExtractor. 이름이 `.rar`이고 내용도 RAR(RAR4·RAR5)인 파일만 다룬다.
 * 암호화는 두 가지를 다룬다. 항목 목록까지 암호화한 아카이브는 열 때 비밀번호를 받아 열리는지로 확인하고, 내용만 암호화한 아카이브는 추출 전에 암호화된 항목 하나를
 * 출력 없이 풀어 보아 확인한다. RAR4는 비밀번호 검증값이 없어 틀린 비밀번호가 데이터·CRC 오류로만 드러나므로 손상과 구분하지 않는다(다시 물을 때 '틀렸거나 손상').
 * 한 아카이브 안에서 항목마다 다른 비밀번호를 쓴 경우는 지원 범위 밖이다 — 확인한 비밀번호가 맞지 않는 항목은 풀 수 없는 항목으로 실패한다.
 */
class RarArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".rar", ignoreCase = true)

    override fun measure(path: String, passwords: ArchivePasswordSession): ArchiveSize =
        withArchive(path, passwords) { archive ->
            var knownBytes = 0L
            var count = 0
            var unknownSizeCount = 0
            for (i in 0 until archive.numberOfItems) {
                if (entryKindOf(archive, i) is RarEntryKind.Folder) continue
                val size = knownSizeOf(archive, i)
                if (size == null) unknownSizeCount++ else knownBytes += size
                count++
            }
            ArchiveSize(knownBytes, count, unknownSizeCount)
        }

    override fun extract(path: String, writer: ArchiveEntryWriter, passwords: ArchivePasswordSession, tracker: ByteProgressTracker) {
        withArchive(path, passwords) { archive ->
            // 암호화된 항목이 있으면 출력을 만들기 전에 비밀번호를 확인해 둔다 — 추출 중 라이브러리가 묻는 비밀번호는 확인된 것만 준다
            smallestEncryptedFileOf(archive)?.let { index -> passwords.verifiedPassword { candidate -> checkPassword(archive, index, candidate, passwords.ensureActive) } }
            RarExtractCallback({ entryKindOf(archive, it) }, { knownSizeOf(archive, it) }, { passwords.verified }, writer, tracker).use { callback ->
                runCollectingFailure(callback) { archive.extract(null, false, callback) }
            }
        }
    }

    /**
     * RAR 파일을 열어 [block]에 IInArchive를 넘기고 사용 후 닫는다. 7-Zip은 내용으로 형식을 판별해 다른 형식도 열어 주므로, RAR이 아니면 IOException으로 실패시킨다.
     * @param path 아카이브 파일 경로
     * @param passwords 항목 목록까지 암호화한 아카이브를 열 때 쓰는 비밀번호 세션
     * @param block 연 아카이브로 할 일
     * @return [block]의 결과
     */
    private fun <T> withArchive(path: String, passwords: ArchivePasswordSession, block: (IInArchive) -> T): T =
        RandomAccessFile(path, "r").use { raf ->
            openArchive(raf, passwords).use { archive ->
                if (!isRar(archive.archiveFormat)) throw IOException("RAR 파일이 아닙니다: ${File(path).name}")
                block(archive)
            }
        }

    /**
     * [raf]를 아카이브로 연다. 여는 중에 라이브러리가 비밀번호를 물으면(항목 목록까지 암호화한 아카이브) 확인된 비밀번호를 주고,
     * 없거나 맞지 않으면 열릴 때까지 묻는다. 목록 암호화는 검증값이 없는 형식(RAR4)도 있어, 비밀번호를 준 뒤의 열기 실패는 '틀렸거나 손상'으로 본다.
     * @param raf 아카이브 파일
     * @param passwords 이 아카이브의 비밀번호 세션
     * @return 연 아카이브
     * @throws SevenZipException 비밀번호와 무관하게 열지 못했을 때
     */
    private fun openArchive(raf: RandomAccessFile, passwords: ArchivePasswordSession): IInArchive {
        val first = RarOpenCallback(passwords.verified)
        try {
            return SevenZip.openInArchive(null, RandomAccessFileInStream(raf), first)
        } catch (e: SevenZipException) {
            if (!first.passwordRequested) throw e
            passwords.forget()
        }
        val password = passwords.verifiedPassword { candidate ->
            try {
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), RarOpenCallback(candidate)).close()
                PasswordCheck.ACCEPTED
            } catch (e: SevenZipException) {
                PasswordCheck.WRONG_OR_DAMAGED
            }
        }
        return SevenZip.openInArchive(null, RandomAccessFileInStream(raf), RarOpenCallback(password))
    }

    /**
     * 암호화된 파일 항목 중 가장 작은 것을 고른다 — 비밀번호 확인으로 끝까지 풀어 볼 항목이므로.
     * @param archive 열린 아카이브
     * @return 항목 번호. 암호화된 파일 항목이 없으면 null
     */
    private fun smallestEncryptedFileOf(archive: IInArchive): Int? =
        (0 until archive.numberOfItems)
            .filter { entryKindOf(archive, it) is RarEntryKind.File && archive.getProperty(it, PropID.ENCRYPTED) == true }
            .minByOrNull { knownSizeOf(archive, it) ?: Long.MAX_VALUE }

    /**
     * [candidate]로 [index] 항목을 출력 없이 끝까지 풀어 비밀번호를 확인한다.
     * @param archive 열린 아카이브
     * @param index 확인에 쓸 암호화된 파일 항목
     * @param candidate 후보 비밀번호
     * @param ensureActive 푸는 동안 작업 취소를 확인하는 함수
     * @return 확인 결과
     * @throws IOException 비밀번호와 무관하게 풀지 못했을 때(지원하지 않는 압축 방식 등)
     */
    private fun checkPassword(archive: IInArchive, index: Int, candidate: String, ensureActive: () -> Unit): PasswordCheck {
        val callback = RarPasswordCheckCallback(candidate, ensureActive)
        runCollectingFailure(callback) { archive.extract(intArrayOf(index), false, callback) }
        return callback.result ?: throw IOException("비밀번호 확인 결과를 받지 못했습니다")
    }

    /**
     * 라이브러리에 콜백을 넘긴 [extract]를 실행하고, 콜백이 처음 던진 예외를 원래 예외로 다시 던진다.
     * @param callback 실패를 보관하는 콜백
     * @param extract 콜백으로 추출하는 호출
     */
    private fun runCollectingFailure(callback: FailureRecordingCallback, extract: () -> Unit) {
        try {
            extract()
        } catch (e: SevenZipException) {
            throw callback.failure ?: e
        }
        // 라이브러리가 콜백의 실패를 항목 결과로만 바꾸고 계속한 경우에도 성공으로 끝내지 않는다
        callback.failure?.let { throw it }
    }

    /** [format]이 이 추출기가 다루는 RAR 형식(RAR4·RAR5)인지 판별한다. */
    private fun isRar(format: ArchiveFormat): Boolean = format == ArchiveFormat.RAR || format == ArchiveFormat.RAR5
}

/**
 * 항목을 [writer]로 만들고 기록하며 진행률을 보고하는 추출 콜백.
 * 라이브러리는 콜백이 던진 예외를 받아 추출을 멈추고 자기 예외로 감싸 다시 던지므로, 처음 던진 예외를 [failure]에 보관해 호출부가 원래 예외로 알리게 한다
 * (입출력 실패는 그 메시지로 작업 실패가 되고, 구현 오류는 결과로 바뀌지 않고 전파된다). 실패한 뒤에 오는 콜백은 같은 예외로 다시 멈춘다.
 * 라이브러리 없이 검증할 수 있도록 아카이브 대신 항목 판별·크기 함수를 받는다.
 * 라이브러리가 암호화된 항목의 비밀번호를 물으면 확인된 비밀번호만 준다 — 확인하지 않은 비밀번호로 출력을 만들지 않도록. 확인된 것이 없으면 실패로 멈춘다.
 * @param entryOf 항목 번호로 항목 종류를 구하는 함수
 * @param sizeOf 항목 번호로 압축 해제 후 크기를 구하는 함수(모르면 null)
 * @param verifiedPassword 이 아카이브에서 확인된 비밀번호를 구하는 함수(없으면 null)
 * @param writer 항목을 추출 루트 아래에 만드는 쓰기 도우미
 * @param tracker 누적 진행률 보고기
 */
internal class RarExtractCallback(
    private val entryOf: (Int) -> RarEntryKind,
    private val sizeOf: (Int) -> Long?,
    private val verifiedPassword: () -> String?,
    private val writer: ArchiveEntryWriter,
    private val tracker: ByteProgressTracker,
) : IArchiveExtractCallback, ICryptoGetTextPassword, FailureRecordingCallback, Closeable {

    /** 콜백에서 던진 예외를 보관하고, 실패한 뒤의 콜백을 같은 예외로 멈추는 기록기. */
    private val recorder = FailureRecorder()

    override val failure: Throwable? get() = recorder.failure

    override fun cryptoGetTextPassword(): String = recorder.record {
        verifiedPassword() ?: throw IOException("확인하지 않은 비밀번호로는 풀지 않습니다")
    }

    /** 현재 항목을 쓰고 있는 출력 스트림. 항목 완료 시 닫고 null로 되돌린다. */
    private var currentOutputStream: OutputStream? = null
    /** 현재 항목의 '/' 구분 경로. */
    private var currentPath = ""
    /** 현재 항목의 압축 해제 후 크기(bytes). 아카이브가 알려주지 않으면 null. */
    private var currentItemSize: Long? = null

    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? = recorder.record {
        if (extractAskMode != ExtractAskMode.EXTRACT) return@record null
        val entry = entryOf(index)
        currentPath = entry.path
        when (entry) {
            is RarEntryKind.Folder -> { writer.createFolder(entry.path); null }
            is RarEntryKind.File -> openFile(entry.path, sizeOf(index))
        }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode) {}

    /** 결과는 마지막 [getStream] 항목의 것이다(SevenZipJBinding 계약). OK가 아니면 그 항목에서 멈추고, 출력을 연 항목이 OK로 끝나야 그 몫을 확정한다. */
    override fun setOperationResult(extractOperationResult: ExtractOperationResult) = recorder.record {
        val output = currentOutputStream
        currentOutputStream = null
        output?.close()
        if (extractOperationResult != ExtractOperationResult.OK) throw IOException("풀 수 없는 항목입니다: $currentPath ($extractOperationResult)")
        if (output != null) tracker.onFileCompleted(currentPath, currentItemSize)
    }

    override fun setTotal(total: Long) {}
    override fun setCompleted(complete: Long) {}

    /** 추출이 중간에 멈춰 닫지 못한 출력 스트림을 닫는다. 취소로 멈췄으면 그 쓰다 만 파일을 지운다. */
    override fun close() {
        val output = currentOutputStream ?: return
        currentOutputStream = null
        output.close()
        if (failure is CancellationException) writer.discardFile(currentPath)
    }

    /**
     * 파일 항목의 출력 스트림을 열고, 기록할 때마다 진행률을 보고하는 스트림을 돌려준다.
     * @param path 항목의 '/' 구분 경로
     * @param size 항목의 압축 해제 후 크기(모르면 null)
     * @return 기록 스트림. [writer]가 건너뛴 항목이면 null(라이브러리에 건너뛰라고 알림)
     */
    private fun openFile(path: String, size: Long?): ISequentialOutStream? {
        currentItemSize = size
        val output = writer.openFile(path, size) ?: return null
        currentOutputStream = output
        tracker.startFile()
        return ISequentialOutStream { data ->
            recorder.record {
                output.write(data)
                tracker.addBytes(data.size.toLong(), path, size)
                data.size
            }
        }
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
 * @throws IOException 경로가 없는 항목일 때
 */
private fun entryKindOf(archive: IInArchive, index: Int): RarEntryKind =
    rarEntryKindOf(archive.getProperty(index, PropID.IS_FOLDER), archive.getProperty(index, PropID.PATH))

/**
 * 항목 속성으로 추출이 그 항목을 어떻게 다루는지 판별한다. 산정·나열·추출이 같은 규칙으로 항목을 고르도록 한 곳에 둔다.
 * 경로가 없는 항목은 놓을 위치를 정할 수 없으므로 읽지 못하는 아카이브로 알린다.
 * 라이브러리 없이 검증할 수 있도록 속성 값만 받는다.
 * @param isFolder 항목의 IS_FOLDER 속성 값
 * @param path 항목의 PATH 속성 값
 * @return '/' 구분으로 정규화한 경로의 [RarEntryKind.Folder] 또는 [RarEntryKind.File]
 * @throws IOException 경로가 없는 항목일 때
 */
internal fun rarEntryKindOf(isFolder: Any?, path: Any?): RarEntryKind {
    if (path !is String) throw IOException(UNNAMED_ENTRY_MESSAGE)
    val normalized = path.replace('\\', '/')
    return if (isFolder as? Boolean == true) RarEntryKind.Folder(normalized) else RarEntryKind.File(normalized)
}

/** 경로가 없는 항목이 있는 아카이브의 실패 메시지. */
private const val UNNAMED_ENTRY_MESSAGE = "이름 없는 항목이 있는 압축 파일입니다"

/** 추출이 RAR 항목 하나를 어떻게 다루는지 나타내는 종류. */
internal sealed interface RarEntryKind {

    /** 아카이브 안의 '/' 구분 경로. */
    val path: String

    /**
     * 추출이 디렉터리로 만드는 폴더 항목.
     * @param path 아카이브 안의 '/' 구분 경로
     */
    data class Folder(override val path: String) : RarEntryKind

    /**
     * 추출이 파일로 만드는 항목.
     * @param path 아카이브 안의 '/' 구분 경로
     */
    data class File(override val path: String) : RarEntryKind
}
