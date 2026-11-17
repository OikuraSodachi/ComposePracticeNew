package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import kotlinx.coroutines.CancellationException
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * 비밀번호를 건 ZIP의 생성([ZipArchiveCompressor])과 해제([ZipArchiveExtractor] + [ArchivePasswordSession])를 검증하는 단위 테스트.
 * AES-256으로 만들어지는지, 필요할 때만 묻는지, 틀리면 이유를 담아 다시 묻는지, 확인 전에는 출력을 만들지 않는지, 항목마다 다른 비밀번호(지원 범위 밖)는 다시 묻지 않고 실패하는지 확인한다.
 */
class ZipPasswordTest {

    /** 아카이브와 압축 해제 결과를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("zip-password-test").toFile()
    /** 압축 해제 목적지. */
    private val out = File(dir, "out")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `비밀번호를 주면 파일 항목을 AES-256으로 암호화해 만든다`() {
        val zip = compressedZip(password = "secret")

        val headers = ZipFile(zip).fileHeaders
        val file = headers.single { !it.isDirectory }
        assertTrue(file.isEncrypted)
        assertEquals(EncryptionMethod.AES, file.encryptionMethod)
        assertEquals(AesKeyStrength.KEY_STRENGTH_256, file.aesExtraDataRecord.aesKeyStrength)
        assertFalse(headers.single { it.isDirectory }.isEncrypted)
    }

    @Test
    fun `비밀번호 없이 만든 ZIP은 암호화하지 않고 풀 때 묻지 않는다`() {
        val zip = compressedZip(password = null)
        val asked = mutableListOf<ArchivePasswordPrompt>()

        extract(zip, session(asked))

        assertFalse(ZipFile(zip).isEncrypted)
        assertEquals(emptyList<ArchivePasswordPrompt>(), asked)
        assertEquals(CONTENT, File(out, "dir/a.txt").readText())
    }

    @Test
    fun `맞는 비밀번호를 받으면 한 번만 묻고 그대로 푼다`() {
        val zip = compressedZip(password = "secret")
        val asked = mutableListOf<ArchivePasswordPrompt>()

        extract(zip, session(asked, "secret"))

        assertEquals(listOf(ArchivePasswordPrompt.FIRST), asked)
        assertEquals(CONTENT, File(out, "dir/a.txt").readText())
    }

    @Test
    fun `틀린 비밀번호를 받으면 틀렸다고 다시 묻고 맞는 비밀번호로 푼다`() {
        val zip = compressedZip(password = "secret")
        val asked = mutableListOf<ArchivePasswordPrompt>()

        extract(zip, session(asked, "wrong", "secret"))

        assertEquals(listOf(ArchivePasswordPrompt.FIRST, ArchivePasswordPrompt.WRONG), asked)
        assertEquals(CONTENT, File(out, "dir/a.txt").readText())
    }

    @Test
    fun `ZipCrypto 항목도 틀린 비밀번호는 다시 묻고 맞는 비밀번호로 푼다`() {
        val zip = zipOf(listOf("a.txt" to "secret"), EncryptionMethod.ZIP_STANDARD)
        val asked = mutableListOf<ArchivePasswordPrompt>()

        extract(zip, session(asked, "wrong", "secret"))

        assertEquals(ArchivePasswordPrompt.FIRST, asked.first())
        // ZipCrypto의 검증값은 1바이트라 틀린 비밀번호가 통과하면 내용 검증에서 드러난다 — 어느 쪽이든 처음 묻기로 되돌아가지 않는다
        assertNotEquals(ArchivePasswordPrompt.FIRST, asked[1])
        assertEquals(CONTENT, File(out, "a.txt").readText())
    }

    @Test
    fun `AES 항목의 내용 손상이 압축 해제 오류로 드러나도 틀렸거나 손상이라고 다시 묻는다`() {
        // 검증값을 통과한 틀린 비밀번호(약 1/65536)도 같은 길이다 — 복호화한 내용이 잘못된 것은 같고, 손상은 결과가 정해져 있어 재현할 수 있다
        val zip = deflatedAesZip().also(::corruptFirstBlockType)
        val asked = mutableListOf<ArchivePasswordPrompt>()

        // 같은 비밀번호로 다시 답해도 손상은 그대로이므로, 세 번째 질문에서 취소해 끝낸다
        assertThrows(CancellationException::class.java) { extract(zip, session(asked, "secret", "secret")) }

        assertEquals(listOf(ArchivePasswordPrompt.FIRST, ArchivePasswordPrompt.WRONG_OR_DAMAGED, ArchivePasswordPrompt.WRONG_OR_DAMAGED), asked)
        assertFalse(File(out, "a.txt").exists())
    }

    @Test
    fun `내용이 손상된 AES 항목은 틀렸거나 손상이라고 다시 묻는다`() {
        val zip = zipOf(listOf("a.txt" to "secret"), EncryptionMethod.AES).also(::corruptLastDataByte)
        val asked = mutableListOf<ArchivePasswordPrompt>()

        // 같은 비밀번호로 다시 답해도 손상은 그대로이므로, 세 번째 질문에서 취소해 끝낸다
        assertThrows(CancellationException::class.java) { extract(zip, session(asked, "secret", "secret")) }

        assertEquals(listOf(ArchivePasswordPrompt.FIRST, ArchivePasswordPrompt.WRONG_OR_DAMAGED, ArchivePasswordPrompt.WRONG_OR_DAMAGED), asked)
        assertFalse(File(out, "a.txt").exists())
    }

    @Test
    fun `비밀번호 질문에서 취소하면 그 항목의 파일을 만들지 않는다`() {
        val zip = compressedZip(password = "secret")

        assertThrows(CancellationException::class.java) { extract(zip, session(mutableListOf())) }

        assertFalse(File(out, "dir/a.txt").exists())
    }

    @Test
    fun `비밀번호를 확인하며 읽는 동안 진행을 보고하고 그 보고에서 취소되면 출력을 만들지 않는다`() {
        val zip = compressedZip(password = "secret")
        val passwords = ArchivePasswordSession(ask = { "secret" }, reportChecking = { throw CancellationException("확인 중 취소") })

        assertThrows(CancellationException::class.java) { extract(zip, passwords) }

        assertFalse(File(out, "dir/a.txt").exists())
    }

    @Test
    fun `항목마다 비밀번호가 다르면 다시 묻지 않고 확인한 비밀번호가 맞지 않는 항목에서 실패한다`() {
        val zip = zipOf(listOf("a.txt" to "first", "b.txt" to "second"), EncryptionMethod.AES)
        val asked = mutableListOf<ArchivePasswordPrompt>()

        assertThrows(IOException::class.java) { extract(zip, session(asked, "first", "second")) }

        assertEquals(listOf(ArchivePasswordPrompt.FIRST), asked)
        assertEquals(CONTENT, File(out, "a.txt").readText())
        assertFalse(File(out, "b.txt").exists())
    }

    @Test
    fun `비밀번호는 앞에 있는 0바이트 항목이 아니라 내용이 있는 항목을 풀어 보아 확인한다`() {
        // 0바이트 항목은 풀 내용이 없어 내용 검증이 비밀번호를 가리지 못한다 — 손상된 b.txt로 확인해야 출력 전에 다시 묻는다
        val zip = zipOf(listOf("empty.txt" to "secret", "b.txt" to "secret"), EncryptionMethod.AES, contents = mapOf("empty.txt" to ""))
            .also { corruptLastDataByte(it, "b.txt") }
        val asked = mutableListOf<ArchivePasswordPrompt>()

        assertThrows(CancellationException::class.java) { extract(zip, session(asked, "secret")) }

        assertEquals(listOf(ArchivePasswordPrompt.FIRST, ArchivePasswordPrompt.WRONG_OR_DAMAGED), asked)
        assertFalse(File(out, "empty.txt").exists())
    }

    /**
     * 폴더 `dir` 아래 `a.txt`를 담은 ZIP을 [ZipArchiveCompressor]로 만든다.
     * @param password 걸 비밀번호. null이면 암호화하지 않는다
     */
    private fun compressedZip(password: String?): File {
        val source = File(dir, "src/dir").apply { mkdirs() }
        val file = File(source, "a.txt").apply { writeText(CONTENT) }
        val entries = listOf(ArchiveSource.Folder("dir"), ArchiveSource.Regular("dir/a.txt", file, file.length()))
        return File(dir, "test.zip").also { zip ->
            ZipArchiveCompressor().write(entries, zip, password, ByteProgressTracker(file.length(), 1, onProgress = {}))
        }
    }

    /**
     * 항목마다 정한 비밀번호로 [method] 암호화한 ZIP을 zip4j로 만든다(항목마다 다른 비밀번호를 쓰려고 항목마다 따로 더한다).
     * @param entries (항목 이름, 비밀번호) 목록
     * @param contents 항목 이름별 내용. 없는 항목은 [CONTENT]
     * @param method 암호화 방식
     */
    private fun zipOf(entries: List<Pair<String, String>>, method: EncryptionMethod, contents: Map<String, String> = emptyMap()): File =
        File(dir, "test.zip").also { zip ->
            entries.forEach { (name, password) ->
                val source = File(dir, "src/$name").apply { parentFile!!.mkdirs(); writeText(contents[name] ?: CONTENT) }
                ZipFile(zip, password.toCharArray()).use { zf ->
                    zf.addFile(source, ZipParameters().apply {
                        // 압축하지 않아 손상시킨 바이트가 압축 해제 오류가 아니라 내용 검증 실패로만 드러나게 한다
                        compressionMethod = CompressionMethod.STORE
                        isEncryptFiles = true
                        encryptionMethod = method
                        if (method == EncryptionMethod.AES) aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    })
                }
            }
        }

    /** 비밀번호 `secret`으로 AES-256 암호화하고 Deflate로 압축한 [LONG_CONTENT] 항목 `a.txt` 하나를 담은 ZIP을 만든다. */
    private fun deflatedAesZip(): File = File(dir, "test.zip").also { zip ->
        val source = File(dir, "src/a.txt").apply { parentFile!!.mkdirs(); writeText(LONG_CONTENT) }
        ZipFile(zip, "secret".toCharArray()).use { zf ->
            zf.addFile(source, ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            })
        }
    }

    /**
     * [zip] 첫 항목 암호문의 첫 바이트에서 비트 하나를 바꿔, 비밀번호 검증값은 맞고 inflate가 첫 블록에서 실패하게 만든다.
     * AES는 CTR 방식이라 암호문의 비트를 바꾸면 평문의 같은 비트가 바뀐다 — Deflate 첫 블록의 종류(BTYPE)가 쓰지 않는 값(11)이 되어,
     * zip4j가 유형 없는 IOException으로 감싸는 "invalid block type"이 난다(HMAC 확인보다 먼저).
     */
    private fun corruptFirstBlockType(zip: File) {
        val header = ZipFile(zip).fileHeaders.first()
        val bytes = zip.readBytes()
        val at = dataStartOf(bytes, header) + AES_256_SALT_LENGTH + AES_VERIFIER_LENGTH
        bytes[at] = (bytes[at].toInt() xor FIRST_BLOCK_TYPE_FLIP).toByte()
        zip.writeBytes(bytes)
    }

    /** [zip]의 [name] 항목(없으면 첫 항목) 암호문 마지막 바이트(HMAC 바로 앞)를 바꿔, 비밀번호 검증값은 맞고 내용 검증만 실패하게 만든다. */
    private fun corruptLastDataByte(zip: File, name: String? = null) {
        val header = ZipFile(zip).fileHeaders.let { headers -> name?.let { n -> headers.single { it.fileName == n } } ?: headers.first() }
        val bytes = zip.readBytes()
        // 데이터 = 솔트·검증값 + 암호문 + HMAC(10바이트)
        val dataEnd = dataStartOf(bytes, header) + header.compressedSize.toInt()
        val at = dataEnd - AES_MAC_LENGTH - 1
        bytes[at] = (bytes[at].toInt() xor 0xFF).toByte()
        zip.writeBytes(bytes)
    }

    /**
     * 항목 데이터가 시작하는 위치를 지역 헤더(30바이트 + 이름 + 추가 필드)를 건너뛰어 구한다.
     * @param bytes ZIP 파일 전체
     * @param header 항목 헤더
     * @return 항목 데이터의 시작 위치
     */
    private fun dataStartOf(bytes: ByteArray, header: FileHeader): Int {
        val localHeaderStart = header.offsetLocalHeader.toInt()
        val nameLength = (bytes[localHeaderStart + 26].toInt() and 0xFF) or ((bytes[localHeaderStart + 27].toInt() and 0xFF) shl 8)
        val extraLength = (bytes[localHeaderStart + 28].toInt() and 0xFF) or ((bytes[localHeaderStart + 29].toInt() and 0xFF) shl 8)
        return localHeaderStart + 30 + nameLength + extraLength
    }

    /**
     * 정해 둔 답을 차례로 주고 받은 이유를 기록하는 비밀번호 세션. 답이 떨어지면 취소한다(사용자가 질문에서 취소한 것과 같은 길).
     * @param asked 받은 이유를 기록할 목록
     * @param answers 차례로 줄 비밀번호
     */
    private fun session(asked: MutableList<ArchivePasswordPrompt>, vararg answers: String): ArchivePasswordSession {
        val remaining = ArrayDeque(answers.toList())
        return ArchivePasswordSession(
            ask = { prompt -> asked += prompt; remaining.removeFirstOrNull() ?: throw CancellationException("질문에서 취소") },
            reportChecking = {},
        )
    }

    /** [zip]을 [out]에 [passwords] 세션으로 푼다. */
    private fun extract(zip: File, passwords: ArchivePasswordSession) = ByteProgressTracker(1, 2, onProgress = {}).let { tracker ->
        ZipArchiveExtractor().extract(zip.path, ArchiveEntryWriter(ExtractionRoot(out), UnexpectedConflictAsk, tracker), passwords, tracker)
    }

    private companion object {
        /** 항목 내용. */
        const val CONTENT = "password-protected-content"
        /** AES 항목 끝의 HMAC 길이(bytes). */
        const val AES_MAC_LENGTH = 10
        /** AES-256 항목 데이터 앞의 솔트 길이(bytes). */
        const val AES_256_SALT_LENGTH = 16
        /** AES 항목 솔트 뒤의 비밀번호 검증값 길이(bytes). */
        const val AES_VERIFIER_LENGTH = 2
        /** Deflate 항목 내용. 고정 허프만이 아닌 블록으로 압축될 만큼 길게 반복한다. */
        val LONG_CONTENT = "password-protected-content ".repeat(2000)
        /** [LONG_CONTENT]를 압축한 첫 블록의 종류(BTYPE 10, 동적 허프만)를 11로 바꾸는 비트(첫 바이트의 둘째 비트). */
        const val FIRST_BLOCK_TYPE_FLIP = 0x02
    }
}
