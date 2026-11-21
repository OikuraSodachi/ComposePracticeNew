package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import kotlinx.coroutines.CancellationException
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * zip4j로 ZIP 아카이브를 산정·추출하는 ArchiveExtractor. 이름이 `.zip`인 파일만 다루며, 속이 ZIP인 다른 형식(.apk, .jar, .docx 등)은 지원 범위 밖이다.
 * 항목 내용은 zip4j가 끝에서 검증한다(CRC, AES는 HMAC) — 손상된 항목을 성공으로 풀지 않는다.
 * 암호화된 항목(AES, ZipCrypto)은 출력을 만들기 전에 비밀번호를 확인한다. 확인은 내용이 있는 가장 작은 암호화 항목([passwordCheckEntryOf])을 끝까지 풀어 보는 것이다 —
 * 비밀번호 검증값(AES 2바이트, ZipCrypto 1바이트)만으로는 틀린 비밀번호가 통과할 수 있고, 그때는 내용 검증에서야 드러나기 때문이다.
 * 심볼릭 링크 항목([isZipSymlink])은 풀지 않고 실패로 알린다(지원 범위 밖, #424).
 * 항목 이름은 UTF-8로만 읽는다. UTF-8이 아닌 이름은 풀지 않고 실패로 알린다(지원 범위 밖). 이름에 원래 U+FFFD가 든 항목도 같은 실패가 된다.
 * 분할 압축(이름 규칙은 [zipNameRoleOf])은 zip4j가 시작 파일에서 다른 볼륨을 찾아 읽는다: PKWARE 분할(`.z01` …)은 내용 목록이 있는 `.zip`에서, 번호 분할은 `.zip.001`에서.
 * 빠진 볼륨은 열 때 확인해 아무것도 풀기 전에 알린다. 번호 분할의 마지막 볼륨이 빠진 것은 이름으로 알 수 없어 손상된 아카이브로 알린다.
 */
class ZipArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun nameRoleOf(fileName: String): ArchiveNameRole? = zipNameRoleOf(fileName)

    override fun measure(path: String, passwords: ArchivePasswordSession): ArchiveSize =
        openZip(path).use { zf ->
            val files = headersOf(zf).filter { !it.isDirectory }
            val knownSizes = files.mapNotNull(::knownSizeOf)
            ArchiveSize(knownBytes = knownSizes.sum(), fileCount = files.size, unknownSizeCount = files.size - knownSizes.size)
        }

    override fun extract(path: String, writer: ArchiveEntryWriter, passwords: ArchivePasswordSession, tracker: ByteProgressTracker) {
        openZip(path).use { zf ->
            val headers = headersOf(zf)
            val checkEntry = passwordCheckEntryOf(headers.filter { !it.isDirectory && !isLink(it) && it.isEncrypted }, ::knownSizeOf)
            headers.forEach { header ->
                if (header.isDirectory) {
                    writer.createFolder(header.fileName)
                    return@forEach
                }
                if (isLink(header)) {
                    writer.skipLink(header.fileName, knownSizeOf(header))
                    return@forEach
                }
                // 비밀번호는 출력을 열기 전에 받는다 — 묻는 동안이나 취소한 뒤에 빈 파일이 남지 않도록
                openEntry(zf, header, checkEntry, passwords).use { input ->
                    val output = writer.openFile(header.fileName, knownSizeOf(header)) ?: return@forEach
                    tracker.startFile()
                    try {
                        output.use { tracker.pump(input, it, header.fileName, knownSizeOf(header)) }
                    } catch (e: CancellationException) {
                        writer.discardFile(header.fileName)
                        throw e
                    }
                }
                tracker.onFileCompleted(header.fileName, knownSizeOf(header))
            }
        }
    }

    /**
     * [path]를 zip4j로 연다. zip4j는 읽기·쓰기 겸용이라 없는 파일과 0바이트 파일을 새로 만들 빈 아카이브로 열므로(그 밖의 손상은 예외로 알린다),
     * 이 두 경우가 항목 없는 성공으로 끝나지 않도록 먼저 확인한다. 분할 압축은 빠진 볼륨이 없는지도 확인한다.
     * @param path 아카이브 파일(분할 압축이면 시작 파일) 경로
     * @return 이름을 UTF-8로 읽도록 연 ZipFile
     * @throws FileNotFoundException [path]가 파일이 아닐 때
     * @throws ZipException [path]가 0바이트 파일일 때
     * @throws MissingArchiveVolumeException 분할 압축의 볼륨이 빠졌을 때
     */
    private fun openZip(path: String): ZipFile {
        val file = File(path)
        if (!file.isFile) throw FileNotFoundException("압축 파일이 없습니다: ${file.name}")
        if (file.length() == 0L) throw ZipException("ZIP 파일이 아니거나 손상된 파일입니다(0바이트): ${file.name}")
        if (zipNameRoleOf(file.name)?.set?.layout == VolumeLayout.NUMBERED) {
            numberedVolumesOf(file)
            return ZipFile(path).apply { charset = Charsets.UTF_8 }
        }
        val zf = ZipFile(path).apply { charset = Charsets.UTF_8 }
        try {
            checkSplitVolumes(zf, file.name)
        } catch (e: IOException) {
            zf.close()
            throw e
        }
        return zf
    }

    /**
     * PKWARE 분할 ZIP이면 zip4j가 내용 목록으로 계산한 볼륨이 모두 있는지 확인한다.
     * @param zf 연 ZIP 파일
     * @param name 실패 메시지에 쓰는 시작 파일 이름
     * @throws MissingArchiveVolumeException 볼륨이 빠졌을 때
     */
    private fun checkSplitVolumes(zf: ZipFile, name: String) {
        if (!zf.isSplitArchive) return
        zf.splitZipFiles.firstOrNull { !it.isFile }?.let { throw MissingArchiveVolumeException(name, it.name) }
    }

    /**
     * 아카이브의 항목을 모두 읽는다. 이름을 해석하지 못한 항목이 있으면 아무것도 풀기 전에 실패한다 —
     * [measure]·[extract]가 같은 실패로 알리고, 일부만 풀린 채 끝나지 않도록.
     * @param zf 연 ZIP 파일
     * @return 아카이브에 담긴 순서의 항목 목록
     * @throws java.util.zip.ZipException 항목 이름을 UTF-8로 해석하지 못했을 때
     */
    private fun headersOf(zf: ZipFile): List<FileHeader> =
        zf.fileHeaders.also { headers ->
            if (headers.any { UNDECODABLE_MARK in it.fileName }) throw java.util.zip.ZipException(UNDECODABLE_ENTRY_MESSAGE)
        }

    /**
     * 항목의 압축 해제 후 크기를 구한다. [measure]와 [extract]가 같은 기준으로 크기를 모르는 항목을 가르도록 한 곳에 둔다.
     * @param header 아카이브 항목
     * @return 크기(bytes), 아카이브가 알려주지 않으면(음수) null
     */
    private fun knownSizeOf(header: FileHeader): Long? = header.uncompressedSize.takeIf { it >= 0 }

    /**
     * 항목이 심볼릭 링크인지 판별한다. ZIP은 링크를 링크 대상 경로를 내용으로 한 파일 항목으로 담고, 링크임은 외부 속성의 Unix 모드로만 알린다.
     * @param header 아카이브 항목
     * @return 링크 항목이면 true
     */
    private fun isLink(header: FileHeader): Boolean = isZipSymlink(header.externalFileAttributes)

    /**
     * 항목의 내용 스트림을 연다. 암호화된 항목은 확인된 비밀번호로 열고, 아직 없으면 [checkEntry]를 끝까지 풀어 보아 확인받는다.
     * 한 아카이브 안에서 항목마다 다른 비밀번호를 쓴 경우는 지원 범위 밖이므로, 확인한 비밀번호가 이 항목의 검증값과 맞지 않으면
     * 다시 묻지 않고 풀 수 없는 항목으로 실패한다(RAR과 같은 범위).
     * @param zf 연 ZIP 파일
     * @param header 열 항목
     * @param checkEntry 비밀번호 확인에 쓸 항목([passwordCheckEntryOf]). 암호화된 파일 항목이 있으면 null이 아니다
     * @param passwords 이 아카이브의 비밀번호 세션
     * @return 항목 내용 스트림. 끝까지 읽으면 zip4j가 내용을 검증한다
     * @throws IOException 확인한 비밀번호가 이 항목에 맞지 않을 때
     */
    private fun openEntry(zf: ZipFile, header: FileHeader, checkEntry: FileHeader?, passwords: ArchivePasswordSession): InputStream {
        if (!header.isEncrypted) return zf.getInputStream(header)
        val password = passwords.verifiedPassword { candidate -> check(zf, checkNotNull(checkEntry), candidate, passwords.reportChecking) }
        return openWith(zf, header, password) ?: throw IOException("확인한 비밀번호가 맞지 않아 풀 수 없는 항목입니다: ${header.fileName}")
    }

    /**
     * [password]로 암호화된 항목을 연다.
     * @param zf 연 ZIP 파일
     * @param header 열 항목
     * @param password 쓸 비밀번호
     * @return 항목 내용 스트림. 비밀번호 검증값이 맞지 않으면 null
     */
    private fun openWith(zf: ZipFile, header: FileHeader, password: String): InputStream? {
        zf.setPassword(password.toCharArray())
        return try {
            zf.getInputStream(header)
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) null else throw e
        }
    }

    /**
     * [candidate]로 항목을 출력 없이 끝까지 풀어 비밀번호를 확인한다.
     * 검증값을 통과해 연 뒤 읽다가 난 실패는 모양과 무관하게 틀렸거나 손상으로 본다 — 틀린 비밀번호로 복호화한 쓰레기 내용은
     * CRC·HMAC 불일치뿐 아니라 inflate 형식 오류·입력 부족(EOFException)으로도 드러나고, zip4j는 그중 일부를 유형 없는 IOException으로 알려
     * 모양을 나열해서는 빠짐없이 가릴 수 없다. 압축 파일을 읽다 난 기기 오류도 같은 IOException이라 구분하지 못하고 다시 묻는다(질문에서 취소로 끝낸다).
     * @param zf 연 ZIP 파일
     * @param header 확인에 쓸 암호화된 항목
     * @param candidate 후보 비밀번호
     * @param reportChecking 읽는 동안 부르는 비밀번호 확인 단계의 진행 보고. 보고가 작업 취소를 확인한다
     * @return 확인 결과
     * @throws IOException 검증값을 확인하기 전(항목을 열 때)에 실패했을 때(그대로 작업 실패)
     */
    private fun check(zf: ZipFile, header: FileHeader, candidate: String, reportChecking: () -> Unit): PasswordCheck {
        val input = openWith(zf, header, candidate) ?: return PasswordCheck.WRONG
        return try {
            input.use { drain(it, reportChecking) }
            PasswordCheck.ACCEPTED
        } catch (e: IOException) {
            PasswordCheck.WRONG_OR_DAMAGED
        }
    }

    /**
     * [input]을 끝까지 읽어 버린다. 버퍼마다 [reportChecking]으로 진행을 보고한다 — 그 보고가 작업 취소를 확인한다.
     * @param input 읽을 스트림
     * @param reportChecking 비밀번호 확인 단계의 진행 보고
     */
    private fun drain(input: InputStream, reportChecking: () -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.read(buffer) != -1) reportChecking()
    }
}

/**
 * ZIP 항목의 외부 속성(리틀 엔디언 4바이트)이 심볼릭 링크를 나타내는지 판별한다. 위쪽 16비트가 Unix 모드다(Info-ZIP 등 Unix에서 만든 ZIP).
 * 만든 시스템(version made by)은 보지 않는다 — Unix가 아닌 시스템으로 표시하고 Unix 모드를 담는 도구도 있고, Windows에서 만든 ZIP은 위쪽 16비트가 0이라 링크로 보이지 않는다.
 * 라이브러리 없이 검증할 수 있도록 속성 바이트만 받는다.
 * @param externalAttributes zip4j가 읽은 항목의 외부 속성. 없으면 null
 * @return 링크 항목이면 true
 */
internal fun isZipSymlink(externalAttributes: ByteArray?): Boolean {
    if (externalAttributes == null || externalAttributes.size < 4) return false
    val unixMode = ((externalAttributes[3].toInt() and 0xFF) shl 8) or (externalAttributes[2].toInt() and 0xFF)
    return isSymlinkMode(unixMode)
}

/** zip4j가 UTF-8로 해석하지 못한 바이트를 바꿔 넣는 문자. */
private const val UNDECODABLE_MARK = '�'

/** 항목 정보를 해석하지 못한 아카이브의 실패 메시지. */
private const val UNDECODABLE_ENTRY_MESSAGE = "항목 이름을 해석할 수 없는 압축 파일입니다(UTF-8이 아닌 이름 등)"