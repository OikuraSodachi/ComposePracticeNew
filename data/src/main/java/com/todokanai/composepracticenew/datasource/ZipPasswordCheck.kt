package com.todokanai.composepracticenew.datasource

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import java.io.IOException
import java.io.InputStream

/**
 * ZIP 항목의 압축 해제 후 크기를 구한다. 추출과 추가가 같은 기준으로 크기를 모르는 항목을 가르도록 한 곳에 둔다.
 * @param header 아카이브 항목
 * @return 크기(bytes), 아카이브가 알려주지 않으면(음수) null
 */
internal fun zipEntrySizeOf(header: FileHeader): Long? = header.uncompressedSize.takeIf { it >= 0 }

/**
 * ZIP 항목이 심볼릭 링크인지 판별한다. ZIP은 링크를 링크 대상 경로를 내용으로 한 파일 항목으로 담고, 링크임은 외부 속성의 Unix 모드로만 알린다.
 * @param header 아카이브 항목
 * @return 링크 항목이면 true
 */
internal fun isZipLinkEntry(header: FileHeader): Boolean = isZipSymlink(header.externalFileAttributes)

/**
 * 비밀번호 확인으로 끝까지 풀어 볼 ZIP 항목을 고른다([passwordCheckEntryOf]). 내용을 풀 수 있는 암호화된 파일 항목(폴더·링크 제외) 중에서 고른다.
 * @param headers 고를 대상 항목
 * @return 확인에 쓸 항목. 암호화된 파일 항목이 없으면 null
 */
internal fun zipPasswordCheckEntryOf(headers: List<FileHeader>): FileHeader? =
    passwordCheckEntryOf(headers.filter { !it.isDirectory && !isZipLinkEntry(it) && it.isEncrypted }, ::zipEntrySizeOf)

/**
 * [password]로 암호화된 항목을 연다.
 * @param zf 연 ZIP 파일
 * @param header 열 항목
 * @param password 쓸 비밀번호
 * @return 항목 내용 스트림. 비밀번호 검증값이 맞지 않으면 null
 */
internal fun openZipEntryWith(zf: ZipFile, header: FileHeader, password: String): InputStream? {
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
internal fun checkZipPassword(zf: ZipFile, header: FileHeader, candidate: String, reportChecking: () -> Unit): PasswordCheck {
    val input = openZipEntryWith(zf, header, candidate) ?: return PasswordCheck.WRONG
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
