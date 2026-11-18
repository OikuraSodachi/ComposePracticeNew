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
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * SevenZipJBinding으로 아카이브를 산정·추출하는 ArchiveExtractor. [names]가 아카이브로 보는 이름이고 내용이 [formats] 중 하나로 열리는 파일만 다룬다.
 * 형식마다 이름 규칙과 받는 형식만 다르므로 형식별 인스턴스를 DI에서 만든다(RAR: `.rar`·`.partN.rar`·`.rNN` → RAR4·RAR5, 7z: `.7z`·`.7z.###` → 7z, ISO: `.iso` → UDF·ISO9660).
 * 분할 압축은 시작 볼륨으로 연다. 라이브러리가 다음 볼륨의 이름을 계산해 요청하면 같은 폴더에서 찾아 주고(RAR), 번호 볼륨은 이어 붙여 하나로 읽는다(7z, [SevenZipInput]).
 * 라이브러리는 세트의 끝에서도 다음 이름을 물을 수 있어 없는 볼륨을 요청한 것만으로는 실패로 보지 않는다. 요청한 볼륨이 없었는데 아카이브를 열지 못했거나,
 * 열었어도 아카이브가 오류를 알리면 그 볼륨이 빠진 것으로 알린다([MissingArchiveVolumeException]).
 * 형식은 라이브러리의 자동 판별에 맡기지 않고 [formats]의 순서대로 지정해 열어, 처음 열린 형식으로 다룬다. 자동 판별은 등록된 형식을 차례로 열어 보고 처음 열린 것을 쓰므로,
 * 여러 형식으로 읽히는 파일(UDF와 ISO9660을 함께 담은 Windows 설치 이미지, MBR을 함께 담은 isohybrid 이미지)이 원하지 않는 형식으로 열릴 수 있어서다 —
 * 하이브리드 이미지가 ISO9660으로 열리면 실제 내용 대신 'UDF로 여세요' 안내 파일만 풀린 채 성공으로 끝난다. 암호화가 없는 형식은 아래 비밀번호 처리가 쓰이지 않는다.
 * 암호화는 두 가지를 다룬다(RAR). 항목 목록까지 암호화한 아카이브는 열 때 비밀번호를 받아 열리는지로 확인하고, 내용만 암호화한 아카이브는 추출 전에 암호화된 항목 하나를
 * 출력 없이 풀어 보아 확인한다. RAR4는 비밀번호 검증값이 없어 틀린 비밀번호가 데이터·CRC 오류로만 드러나므로 손상과 구분하지 않는다(다시 물을 때 '틀렸거나 손상').
 * 한 아카이브 안에서 항목마다 다른 비밀번호를 쓴 경우는 지원 범위 밖이다 — 확인한 비밀번호가 맞지 않는 항목은 풀 수 없는 항목으로 실패한다.
 * @param names 파일 이름의 역할을 정하는 이름 규칙. 아카이브 이름이 아니면 null
 * @param formats 이 순서대로 지정해 열어 보는 7-Zip 형식. 여러 형식으로 읽히는 파일은 앞의 형식으로 다룬다
 * @param label 열지 못했을 때 실패 메시지에 쓰는 형식 이름
 */
class SevenZipArchiveExtractor(
    private val names: (String) -> ArchiveNameRole?,
    private val formats: List<ArchiveFormat>,
    private val label: String,
) : ArchiveExtractor {

    override fun nameRoleOf(fileName: String): ArchiveNameRole? = names(fileName)

    override fun measure(path: String, passwords: ArchivePasswordSession): ArchiveSize =
        withArchive(path, passwords) { archive ->
            var knownBytes = 0L
            var count = 0
            var unknownSizeCount = 0
            for (i in 0 until archive.numberOfItems) {
                if (entryKindOf(archive, i) is SevenZipEntryKind.Folder) continue
                val size = knownSizeOf(archive, i)
                if (size == null) unknownSizeCount++ else knownBytes += size
                count++
            }
            ArchiveSize(knownBytes, count, unknownSizeCount)
        }

    override fun extract(path: String, writer: ArchiveEntryWriter, passwords: ArchivePasswordSession, tracker: ByteProgressTracker) {
        withArchive(path, passwords) { archive ->
            // 암호화된 항목이 있으면 출력을 만들기 전에 비밀번호를 확인해 둔다 — 추출 중 라이브러리가 묻는 비밀번호는 확인된 것만 준다
            passwordCheckIndexOf(archive)?.let { index -> passwords.verifiedPassword { candidate -> checkPassword(archive, index, candidate, passwords.reportChecking) } }
            SevenZipExtractCallback({ entryKindOf(archive, it) }, { knownSizeOf(archive, it) }, { passwords.verified }, writer, tracker).use { callback ->
                runCollectingFailure(callback) { archive.extract(null, false, callback) }
            }
        }
    }

    /**
     * 아카이브를 열어 [block]에 IInArchive를 넘기고 사용 후 닫는다. 분할 압축은 다른 볼륨도 추출이 끝날 때까지 열어 둔다.
     * @param path 아카이브 파일(분할 압축이면 시작 볼륨) 경로
     * @param passwords 항목 목록까지 암호화한 아카이브를 열 때 쓰는 비밀번호 세션
     * @param block 연 아카이브로 할 일
     * @return [block]의 결과
     * @throws MissingArchiveVolumeException 분할 압축의 볼륨이 빠졌을 때
     */
    private fun <T> withArchive(path: String, passwords: ArchivePasswordSession, block: (IInArchive) -> T): T {
        val file = File(path)
        return inputOf(file).use { input ->
            openArchive(input, passwords, file.name).use { archive ->
                checkVolumes(archive, input, file.name)
                block(archive)
            }
        }
    }

    /**
     * [file]을 읽을 입력을 이름이 정한 볼륨 읽기 방식으로 만든다.
     * @param file 아카이브 파일(분할 압축이면 시작 볼륨)
     * @return 입력
     * @throws MissingArchiveVolumeException 번호 볼륨의 가운데가 빠졌을 때
     */
    private fun inputOf(file: File): SevenZipInput = when (names(file.name)?.set?.layout) {
        VolumeLayout.NUMBERED -> SevenZipInput.Numbered(numberedVolumesOf(file))
        VolumeLayout.SELF_LOCATING, null -> SevenZipInput.SelfLocating(file)
    }

    /**
     * [input]을 [formats]의 순서대로 지정해 열어 보고, 처음 열린 형식의 아카이브를 돌려준다.
     * @param input 아카이브 입력
     * @param passwords 이 아카이브의 비밀번호 세션
     * @param name 실패 메시지에 쓰는 파일 이름
     * @return 연 아카이브
     * @throws MissingArchiveVolumeException 어느 형식으로도 열지 못했고, 여는 중에 요청한 볼륨이 없었을 때
     * @throws IOException 어느 형식으로도 열지 못했을 때
     */
    private fun openArchive(input: SevenZipInput, passwords: ArchivePasswordSession, name: String): IInArchive {
        for (format in formats) openAs(format, input, passwords)?.let { return it }
        input.missingVolume?.let { throw MissingArchiveVolumeException(name, it) }
        throw IOException("$label 파일이 아닙니다: $name")
    }

    /**
     * 연 아카이브가 오류를 알리는데 여는 중에 요청한 볼륨이 없었으면 그 볼륨이 빠진 것으로 알린다 —
     * 볼륨이 빠진 분할 압축이 있는 볼륨까지로 열려, 빠진 볼륨에 든 항목 없이 풀리지 않도록.
     * @param archive 연 아카이브
     * @param input 아카이브 입력
     * @param name 실패 메시지에 쓰는 파일 이름
     * @throws MissingArchiveVolumeException 볼륨이 빠졌을 때
     */
    private fun checkVolumes(archive: IInArchive, input: SevenZipInput, name: String) {
        val missing = input.missingVolume ?: return
        val errorFlags = archive.getArchiveProperty(PropID.ERROR_FLAGS) as? Int ?: 0
        if (archive.getArchiveProperty(PropID.ERROR) != null || errorFlags != 0) throw MissingArchiveVolumeException(name, missing)
    }

    /**
     * [input]을 [format] 아카이브로 연다. 여는 중에 라이브러리가 비밀번호를 물으면(항목 목록까지 암호화한 아카이브) 확인된 비밀번호를 주고,
     * 없거나 맞지 않으면 열릴 때까지 묻는다. 목록 암호화는 검증값이 없는 형식(RAR4)도 있어, 비밀번호를 준 뒤의 열기 실패는 '틀렸거나 손상'으로 본다.
     * 비밀번호를 묻지 않고 열지 못했으면 이 형식이 아닌 것으로 본다.
     * @param format 지정해 열 형식
     * @param input 아카이브 입력
     * @param passwords 이 아카이브의 비밀번호 세션
     * @return 연 아카이브. [format]으로 열리지 않으면 null
     */
    private fun openAs(format: ArchiveFormat, input: SevenZipInput, passwords: ArchivePasswordSession): IInArchive? {
        val first = SevenZipOpenCallback(passwords.verified, input)
        try {
            return SevenZip.openInArchive(format, input.stream(), first)
        } catch (e: SevenZipException) {
            // 확인된 비밀번호로도 열지 못했으면 아래 verifiedPassword가 그 비밀번호를 그대로 돌려줘 다시 열기가 실패로 끝난다
            if (!first.passwordRequested) return null
        }
        val password = passwords.verifiedPassword { candidate ->
            try {
                SevenZip.openInArchive(format, input.stream(), SevenZipOpenCallback(candidate, input)).close()
                PasswordCheck.ACCEPTED
            } catch (e: SevenZipException) {
                PasswordCheck.WRONG_OR_DAMAGED
            }
        }
        return SevenZip.openInArchive(format, input.stream(), SevenZipOpenCallback(password, input))
    }

    /**
     * 암호화된 파일 항목 중 비밀번호 확인으로 끝까지 풀어 볼 항목을 고른다(기준은 [passwordCheckEntryOf]).
     * @param archive 열린 아카이브
     * @return 항목 번호. 암호화된 파일 항목이 없으면 null
     */
    private fun passwordCheckIndexOf(archive: IInArchive): Int? =
        passwordCheckEntryOf(
            (0 until archive.numberOfItems).filter { entryKindOf(archive, it) is SevenZipEntryKind.File && archive.getProperty(it, PropID.ENCRYPTED) == true },
        ) { knownSizeOf(archive, it) }

    /**
     * [candidate]로 [index] 항목을 출력 없이 끝까지 풀어 비밀번호를 확인한다.
     * @param archive 열린 아카이브
     * @param index 확인에 쓸 암호화된 파일 항목
     * @param candidate 후보 비밀번호
     * @param reportChecking 푸는 동안 부르는 비밀번호 확인 단계의 진행 보고. 보고가 작업 취소를 확인한다
     * @return 확인 결과
     * @throws IOException 비밀번호와 무관하게 풀지 못했을 때(지원하지 않는 압축 방식 등)
     */
    private fun checkPassword(archive: IInArchive, index: Int, candidate: String, reportChecking: () -> Unit): PasswordCheck {
        val callback = SevenZipPasswordCheckCallback(candidate, reportChecking)
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

    companion object {
        /** RAR 파일(분할 압축 포함, 이름 규칙은 [rarNameRoleOf])을 RAR4·RAR5로 다루는 추출기를 만든다. */
        fun rar(): SevenZipArchiveExtractor = SevenZipArchiveExtractor(::rarNameRoleOf, listOf(ArchiveFormat.RAR, ArchiveFormat.RAR5), "RAR")

        /** 7z 파일(번호 분할 포함, 이름 규칙은 [sevenZipNameRoleOf])을 다루는 추출기를 만든다. */
        fun sevenZip(): SevenZipArchiveExtractor = SevenZipArchiveExtractor(::sevenZipNameRoleOf, listOf(ArchiveFormat.SEVEN_ZIP), "7Z")

        /** `.iso` 파일을 UDF·ISO9660으로 다루는 추출기를 만든다. UDF와 ISO9660을 함께 담은 이미지(Windows 설치 이미지 등)는 실제 내용이 UDF 쪽에 있어 UDF를 먼저 연다. */
        fun iso(): SevenZipArchiveExtractor = SevenZipArchiveExtractor({ singleFileRoleOf(it, ".iso") }, listOf(ArchiveFormat.UDF, ArchiveFormat.ISO), "ISO")
    }
}

/**
 * 항목을 [writer]로 만들고 기록하며 진행률을 보고하는 추출 콜백.
 * 라이브러리는 콜백이 던진 예외를 받아 추출을 멈추고 자기 예외로 감싸 다시 던지므로, 처음 던진 예외를 [failure]에 보관해 호출부가 원래 예외로 알리게 한다
 * (입출력 실패는 그 메시지로 작업 실패가 되고, 구현 오류는 결과로 바뀌지 않고 전파된다). 실패한 뒤에 오는 콜백은 같은 예외로 다시 멈춘다.
 * 라이브러리 없이 검증할 수 있도록 아카이브 대신 항목 판별·크기 함수를 받는다.
 * 라이브러리가 진행을 알릴 때([setCompleted])마다 지금 진행을 다시 보고한다 — solid RAR은 건너뛴 항목도 출력 없이 끝까지 푸는데 그동안 기록이 없어,
 * 기록할 때의 보고만으로는 작업 취소가 듣지 않는다(진행 보고가 취소를 확인하는 자리이므로).
 * 라이브러리가 암호화된 항목의 비밀번호를 물으면 확인된 비밀번호만 준다 — 확인하지 않은 비밀번호로 출력을 만들지 않도록. 확인된 것이 없으면 실패로 멈춘다.
 * @param entryOf 항목 번호로 항목 종류를 구하는 함수
 * @param sizeOf 항목 번호로 압축 해제 후 크기를 구하는 함수(모르면 null)
 * @param verifiedPassword 이 아카이브에서 확인된 비밀번호를 구하는 함수(없으면 null)
 * @param writer 항목을 추출 루트 아래에 만드는 쓰기 도우미
 * @param tracker 누적 진행률 보고기
 */
internal class SevenZipExtractCallback(
    private val entryOf: (Int) -> SevenZipEntryKind,
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
            is SevenZipEntryKind.Folder -> { currentItemSize = null; writer.createFolder(entry.path); null }
            is SevenZipEntryKind.File -> openFile(entry.path, sizeOf(index))
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
    override fun setCompleted(complete: Long) = recorder.record { tracker.reportCurrent(currentPath, currentItemSize) }

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
private fun entryKindOf(archive: IInArchive, index: Int): SevenZipEntryKind =
    sevenZipEntryKindOf(archive.getProperty(index, PropID.IS_FOLDER), archive.getProperty(index, PropID.PATH))

/**
 * 항목 속성으로 추출이 그 항목을 어떻게 다루는지 판별한다. 산정·나열·추출이 같은 규칙으로 항목을 고르도록 한 곳에 둔다.
 * 경로가 없는 항목은 놓을 위치를 정할 수 없으므로 읽지 못하는 아카이브로 알린다.
 * 라이브러리 없이 검증할 수 있도록 속성 값만 받는다.
 * @param isFolder 항목의 IS_FOLDER 속성 값
 * @param path 항목의 PATH 속성 값
 * @return '/' 구분으로 정규화한 경로의 [SevenZipEntryKind.Folder] 또는 [SevenZipEntryKind.File]
 * @throws IOException 경로가 없는 항목일 때
 */
internal fun sevenZipEntryKindOf(isFolder: Any?, path: Any?): SevenZipEntryKind {
    if (path !is String) throw IOException(UNNAMED_ENTRY_MESSAGE)
    val normalized = path.replace('\\', '/')
    return if (isFolder as? Boolean == true) SevenZipEntryKind.Folder(normalized) else SevenZipEntryKind.File(normalized)
}

/** 경로가 없는 항목이 있는 아카이브의 실패 메시지. */
private const val UNNAMED_ENTRY_MESSAGE = "이름 없는 항목이 있는 압축 파일입니다"

/** 추출이 아카이브 항목 하나를 어떻게 다루는지 나타내는 종류. */
internal sealed interface SevenZipEntryKind {

    /** 아카이브 안의 '/' 구분 경로. */
    val path: String

    /**
     * 추출이 디렉터리로 만드는 폴더 항목.
     * @param path 아카이브 안의 '/' 구분 경로
     */
    data class Folder(override val path: String) : SevenZipEntryKind

    /**
     * 추출이 파일로 만드는 항목.
     * @param path 아카이브 안의 '/' 구분 경로
     */
    data class File(override val path: String) : SevenZipEntryKind
}
