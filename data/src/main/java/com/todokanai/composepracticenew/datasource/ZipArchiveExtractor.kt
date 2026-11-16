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
 * 암호화된 항목(AES, ZipCrypto)은 출력을 만들기 전에 비밀번호를 확인한다. 확인은 그 항목을 끝까지 풀어 보는 것이다 —
 * 비밀번호 검증값(AES 2바이트, ZipCrypto 1바이트)만으로는 틀린 비밀번호가 통과할 수 있고, 그때는 내용 검증에서야 드러나기 때문이다.
 * 항목 이름은 UTF-8로만 읽는다. UTF-8이 아닌 이름은 풀지 않고 실패로 알린다(지원 범위 밖). 이름에 원래 U+FFFD가 든 항목도 같은 실패가 된다.
 */
class ZipArchiveExtractor @Inject constructor() : ArchiveExtractor {

    override fun supports(path: String): Boolean = path.endsWith(".zip", ignoreCase = true)

    override fun measure(path: String, passwords: ArchivePasswordSession): ArchiveSize =
        openZip(path).use { zf ->
            val files = headersOf(zf).filter { !it.isDirectory }
            val knownSizes = files.mapNotNull(::knownSizeOf)
            ArchiveSize(knownBytes = knownSizes.sum(), fileCount = files.size, unknownSizeCount = files.size - knownSizes.size)
        }

    override fun extract(path: String, writer: ArchiveEntryWriter, passwords: ArchivePasswordSession, tracker: ByteProgressTracker) {
        openZip(path).use { zf ->
            headersOf(zf).forEach { header ->
                if (header.isDirectory) {
                    writer.createFolder(header.fileName)
                    return@forEach
                }
                // 비밀번호는 출력을 열기 전에 받는다 — 묻는 동안이나 취소한 뒤에 빈 파일이 남지 않도록
                openEntry(zf, header, passwords).use { input ->
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
     * 이 두 경우가 항목 없는 성공으로 끝나지 않도록 먼저 확인한다.
     * @param path 아카이브 파일 경로
     * @return 이름을 UTF-8로 읽도록 연 ZipFile
     * @throws FileNotFoundException [path]가 파일이 아닐 때
     * @throws ZipException [path]가 0바이트 파일일 때
     */
    private fun openZip(path: String): ZipFile {
        val file = File(path)
        if (!file.isFile) throw FileNotFoundException("압축 파일이 없습니다: ${file.name}")
        if (file.length() == 0L) throw ZipException("ZIP 파일이 아니거나 손상된 파일입니다(0바이트): ${file.name}")
        return ZipFile(path).apply { charset = Charsets.UTF_8 }
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
     * 항목의 내용 스트림을 연다. 암호화된 항목은 확인된 비밀번호로 연다 — 한 아카이브 안에서 항목마다 다른 비밀번호를 쓴 경우는 지원 범위 밖이므로,
     * 앞 항목에서 확인한 비밀번호가 이 항목의 검증값과 맞지 않으면 다시 묻지 않고 풀 수 없는 항목으로 실패한다(RAR과 같은 범위).
     * @param zf 연 ZIP 파일
     * @param header 열 항목
     * @param passwords 이 아카이브의 비밀번호 세션
     * @return 항목 내용 스트림. 끝까지 읽으면 zip4j가 내용을 검증한다
     * @throws IOException 확인한 비밀번호가 이 항목에 맞지 않을 때
     */
    private fun openEntry(zf: ZipFile, header: FileHeader, passwords: ArchivePasswordSession): InputStream {
        if (!header.isEncrypted) return zf.getInputStream(header)
        passwords.verified?.let { known ->
            return openWith(zf, header, known) ?: throw IOException("확인한 비밀번호가 맞지 않아 풀 수 없는 항목입니다: ${header.fileName}")
        }
        val password = passwords.verifiedPassword { candidate -> check(zf, header, candidate, passwords.ensureActive) }
        // 방금 같은 비밀번호로 끝까지 풀어 본 항목이라 검증값이 맞지 않을 수 없다
        return openWith(zf, header, password) ?: throw IOException("확인한 비밀번호로 항목을 열지 못했습니다: ${header.fileName}")
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
     * @param zf 연 ZIP 파일
     * @param header 확인에 쓸 암호화된 항목
     * @param candidate 후보 비밀번호
     * @param ensureActive 읽는 동안 작업 취소를 확인하는 함수
     * @return 확인 결과
     * @throws IOException 내용 검증이 아닌 이유로 읽지 못했을 때(그대로 작업 실패)
     */
    private fun check(zf: ZipFile, header: FileHeader, candidate: String, ensureActive: () -> Unit): PasswordCheck {
        val input = openWith(zf, header, candidate) ?: return PasswordCheck.WRONG
        return try {
            input.use { drain(it, ensureActive) }
            PasswordCheck.ACCEPTED
        } catch (e: IOException) {
            if (isContentVerificationFailure(e)) PasswordCheck.WRONG_OR_DAMAGED else throw e
        }
    }

    /**
     * [input]을 끝까지 읽어 버린다. 버퍼마다 [ensureActive]로 취소를 확인한다.
     * @param input 읽을 스트림
     * @param ensureActive 작업 취소를 확인하는 함수
     */
    private fun drain(input: InputStream, ensureActive: () -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.read(buffer) != -1) ensureActive()
    }

    /**
     * [e]가 풀어 낸 내용의 검증 실패인지 판별한다 — 틀린 비밀번호와 손상을 구분할 수 없는 실패다.
     * ZipCrypto 항목의 CRC 불일치는 WRONG_PASSWORD, 그 밖의 CRC 불일치는 CHECKSUM_MISMATCH 유형이다.
     * AES 항목의 HMAC 불일치는 zip4j가 유형 없는 IOException으로 알리므로 메시지로 가린다(zip4j를 올릴 때 테스트가 메시지 변경을 잡는다).
     * @param e 항목을 읽다 난 예외
     * @return 내용 검증 실패면 true
     */
    private fun isContentVerificationFailure(e: IOException): Boolean = when (e) {
        is ZipException -> e.type == ZipException.Type.WRONG_PASSWORD || e.type == ZipException.Type.CHECKSUM_MISMATCH
        else -> e.message?.contains(AES_VERIFICATION_FAILED) == true
    }
}

/** zip4j가 UTF-8로 해석하지 못한 바이트를 바꿔 넣는 문자. */
private const val UNDECODABLE_MARK = '�'

/** 항목 정보를 해석하지 못한 아카이브의 실패 메시지. */
private const val UNDECODABLE_ENTRY_MESSAGE = "항목 이름을 해석할 수 없는 압축 파일입니다(UTF-8이 아닌 이름 등)"

/** zip4j(AesCipherInputStream)가 AES 항목의 HMAC 불일치를 알리는 메시지의 일부. */
private const val AES_VERIFICATION_FAILED = "aes verification failed"
