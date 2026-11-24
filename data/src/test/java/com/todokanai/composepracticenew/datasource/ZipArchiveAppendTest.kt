package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [ZipArchiveAppend]가 로컬 항목을 ZIP 안의 폴더에 넣고, 겹친 이름을 쓰기 전에 묻고, 실패·취소하면 원본을 그대로 두고 임시 사본만 지우는지 검증하는 단위 테스트.
 */
class ZipArchiveAppendTest {

    /** 압축 파일과 넣을 항목을 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("zip-append-test").toFile()
    /** 넣을 로컬 항목을 둘 폴더 — 압축 파일과 다른 폴더라 압축 파일 자신이 넣을 항목에 섞이지 않는다. */
    private val local: File = File(dir, "local").apply { mkdirs() }
    /** 압축 파일을 둘 폴더. */
    private val archives: File = File(dir, "archives").apply { mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `이름으로 단일 ZIP만 추가할 수 있다고 판정한다`() {
        val append = appendOf()

        assertTrue(append.canAddTo("a.zip"))
        assertTrue(append.canAddTo("A.ZIP"))
        assertFalse(append.canAddTo("a.zip.001"))
        assertFalse(append.canAddTo("a.z01"))
        assertFalse(append.canAddTo("a.rar"))
        assertFalse(append.canAddTo("a.7z"))
    }

    @Test
    fun `안쪽 폴더에 파일과 폴더를 넣고 기존 항목은 그대로 둔다`() {
        val zip = zipOf("a.zip", "d/old.txt")
        val file = localFile("new.txt", "new")
        localFile("sub/inner.txt", "inner")

        val result = add(setOf(file.path, File(local, "sub").path), zip, listOf("d"))

        assertEquals(IoResult.Done, result)
        assertEquals(mapOf("d/old.txt" to "d/old.txt", "d/new.txt" to "new", "d/sub/" to "", "d/sub/inner.txt" to "inner"), contentsOf(zip))
        assertEquals(listOf("a.zip"), archives.list()!!.toList())
    }

    @Test
    fun `같은 이름의 폴더는 묻지 않고 합친다 — 폴더 항목 없이 경로에만 있는 폴더도 같다`() {
        val zip = zipOf("a.zip", "sub/old.txt")
        localFile("sub/new.txt", "new")

        val result = add(setOf(File(local, "sub").path), zip)

        assertEquals(IoResult.Done, result)
        assertEquals(mapOf("sub/old.txt" to "sub/old.txt", "sub/new.txt" to "new"), contentsOf(zip))
    }

    @Test
    fun `같은 이름의 파일을 덮어쓰기로 답하면 기존 항목을 새 내용으로 바꾼다`() {
        val zip = zipOf("a.zip", "a.txt", "b.txt")
        val asked = mutableListOf<WriteConflict>()

        val result = add(setOf(localFile("a.txt", "new").path), zip, resolver = answering(ConflictChoice.PROCEED, asked))

        assertEquals(IoResult.Done, result)
        assertEquals(listOf(WriteConflict("a.txt", "a.zip", WriteConflictKind.FILE)), asked)
        assertEquals(mapOf("a.txt" to "new", "b.txt" to "b.txt"), contentsOf(zip))
    }

    @Test
    fun `같은 이름의 파일을 건너뛰면 압축 파일을 다시 쓰지 않는다`() {
        val zip = zipOf("a.zip", "a.txt")
        val before = zip.readBytes()

        val result = add(setOf(localFile("a.txt", "new").path), zip, resolver = answering(ConflictChoice.SKIP))

        assertEquals(IoResult.Done, result)
        assertArrayEquals(before, zip.readBytes())
    }

    @Test
    fun `파일 자리에 폴더를 넣으려 하면 건너뛰기만 묻고 그 안쪽도 넣지 않는다`() {
        val zip = zipOf("a.zip", "x")
        val before = zip.readBytes()
        localFile("x/inner.txt", "inner")
        val asked = mutableListOf<WriteConflict>()

        val result = add(setOf(File(local, "x").path), zip, resolver = answering(ConflictChoice.SKIP, asked))

        assertEquals(IoResult.Done, result)
        assertEquals(listOf(WriteConflict("x", "a.zip", WriteConflictKind.TYPE_MISMATCH)), asked)
        assertArrayEquals(before, zip.readBytes())
    }

    @Test
    fun `넣을 항목에 압축 파일 자신이 있으면 바꾸지 않고 실패한다`() {
        val zip = zipOf("a.zip", "a.txt")
        val before = zip.readBytes()

        val itself = add(setOf(zip.path), zip)
        val inFolder = add(setOf(archives.path), zip)

        assertEquals(IoResult.ItemFailed(FailureReason.ArchiveAddedIntoItself("a.zip")), itself)
        assertEquals(IoResult.ItemFailed(FailureReason.ArchiveAddedIntoItself("a.zip")), inFolder)
        assertArrayEquals(before, zip.readBytes())
    }

    @Test
    fun `ZIP이 아닌 압축 파일은 열지 않고 실패한다`() {
        val rar = File(archives, "a.rar").apply { writeText("not a zip") }

        val result = add(setOf(localFile("a.txt", "a").path), rar)

        assertEquals(IoResult.ItemFailed(FailureReason.ArchiveNotAppendable("a.rar")), result)
        assertEquals("not a zip", rar.readText())
    }

    @Test
    fun `암호화된 항목이 있으면 비밀번호를 확인한 뒤 새 파일도 그 비밀번호로 AES 암호화한다`() {
        val zip = zipOf("a.zip", "plain.txt")
        ZipFile(zip, "pw".toCharArray()).use { it.addFile(localFile("secret.txt", "hidden"), aes()) }
        val prompts = mutableListOf<ArchivePasswordPrompt>()
        val passwords = ArchivePasswordProvider { _, prompt -> prompts += prompt; if (prompts.size == 1) "wrong" else "pw" }

        val result = add(setOf(localFile("new.txt", "new").path), zip, passwords = passwords)

        assertEquals(IoResult.Done, result)
        assertEquals(listOf(ArchivePasswordPrompt.FIRST, ArchivePasswordPrompt.WRONG), prompts)
        ZipFile(zip).use { zf -> assertTrue(zf.getFileHeader("new.txt").isEncrypted) }
        assertEquals("new", contentsOf(zip, "pw")["new.txt"])
    }

    @Test
    fun `쓰는 중에 취소하면 취소를 전파하고 원본을 그대로 두며 임시 사본을 지운다`() {
        val zip = zipOf("a.zip", "a.txt")
        val before = zip.readBytes()
        val cancelled = CancellationException("취소")
        val onProgress: (ProgressPhase) -> Unit = { phase -> if (phase is ProgressPhase.Bytes && phase.currentFile?.name == "new.txt") throw cancelled }

        val thrown = assertThrows(CancellationException::class.java) {
            add(setOf(localFile("new.txt", "x".repeat(100_000)).path), zip, onProgress = onProgress)
        }

        // withContext를 건너며 코루틴이 스택 추적을 복원한 사본을 던질 수 있어 인스턴스 대신 메시지로 확인한다 — 입출력 실패로 바뀌지 않았다는 것이 요점이다
        assertEquals(cancelled.message, thrown.message)
        assertArrayEquals(before, zip.readBytes())
        assertEquals(listOf("a.zip"), archives.list()!!.toList())
    }

    @Test
    fun `교체 전에 입출력 실패하면 실패로 알리고 원본을 그대로 두며 임시 사본을 지운다`() {
        val zip = zipOf("a.zip", "a.txt")
        val before = zip.readBytes()
        val append = ZipArchiveAppend({ Long.MAX_VALUE }, { throw IOException("기록 실패") }, UnexpectedLeftoverDiscarder)

        val result = add(setOf(localFile("new.txt", "new").path), zip, append = append)

        assertTrue(result is IoResult.ItemFailed)
        assertArrayEquals(before, zip.readBytes())
        assertEquals(listOf("a.zip"), archives.list()!!.toList())
    }

    @Test
    fun `공간이 부족하면 아무것도 쓰지 않고 실패한다`() {
        val zip = zipOf("a.zip", "a.txt")
        val before = zip.readBytes()
        val append = ZipArchiveAppend({ 0L }, {}, UnexpectedLeftoverDiscarder)

        val result = add(setOf(localFile("new.txt", "new").path), zip, append = append)

        assertEquals(IoResult.ItemFailed(FailureReason.NotEnoughSpace(zip.length() + 3, 0)), result)
        assertArrayEquals(before, zip.readBytes())
        assertEquals(listOf("a.zip"), archives.list()!!.toList())
    }

    /** [sources]를 [zip]의 [folderNames] 폴더에 넣는다. 질문은 기본적으로 오면 테스트가 실패한다. */
    private fun add(
        sources: Set<String>,
        zip: File,
        folderNames: List<String> = emptyList(),
        resolver: WriteConflictResolver = UnexpectedConflictResolver,
        passwords: ArchivePasswordProvider = UnexpectedPasswordProvider,
        onProgress: (ProgressPhase) -> Unit = {},
        append: ZipArchiveAppend = appendOf(),
    ): IoResult<Unit> = runBlocking { append.addFiles(sources, zip.path, folderNames, resolver, passwords, onProgress) }

    /** 공간이 충분하고 장치 기록을 건너뛰는 검증 대상. */
    private fun appendOf() = ZipArchiveAppend({ Long.MAX_VALUE }, {}, UnexpectedLeftoverDiscarder)

    /** 모든 겹침에 [choice]로 답하고, 받은 질문을 [asked]에 남기는 질문 통로. 건너뛰기만 할 수 있는 겹침에는 건너뛰기로 답한다. */
    private fun answering(choice: ConflictChoice, asked: MutableList<WriteConflict> = mutableListOf()) = WriteConflictResolver { conflict ->
        asked += conflict
        if (conflict.kind.canProceed) choice else ConflictChoice.SKIP
    }

    /** [local] 아래 [path]에 [text]를 담은 파일을 만든다. */
    private fun localFile(path: String, text: String): File =
        File(local, path).apply { parentFile!!.mkdirs(); writeText(text) }

    /** 항목 이름을 내용으로 한 [entryNames]를 담은 [name] ZIP 파일을 만든다. */
    private fun zipOf(name: String, vararg entryNames: String): File =
        File(archives, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                entryNames.forEach { entryName ->
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(entryName.toByteArray())
                    zos.closeEntry()
                }
            }
        }

    /** [zip]의 항목 이름과 내용. 폴더 항목의 내용은 빈 문자열이다. */
    private fun contentsOf(zip: File, password: String? = null): Map<String, String> =
        ZipFile(zip, password?.toCharArray()).use { zf ->
            zf.fileHeaders.associate { header ->
                header.fileName to if (header.isDirectory) "" else zf.getInputStream(header).use { String(it.readBytes()) }
            }
        }

    /** AES-256 암호화 쓰기 설정. */
    private fun aes() = ZipParameters().apply {
        isEncryptFiles = true
        encryptionMethod = EncryptionMethod.AES
        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
    }
}
