package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [LocalArchiveExtraction.extractEntries]가 내용 보기에서 고른 항목만 고른 폴더 기준 위치로 풀고,
 * 고른 뒤 압축 파일이 바뀌었으면 아무것도 풀지 않으며, 고른 항목 밖의 암호화 항목 때문에 비밀번호를 묻지 않는지 검증하는 단위 테스트.
 */
class LocalArchiveExtractionEntriesTest {

    /** 아카이브와 목적지를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("archive-entries-test").toFile()
    /** 풀 목적지. */
    private val dest: File = File(dir, "dest").apply { mkdirs() }
    /** 검증 대상. ZIP 추출기만 둔다. */
    private val extraction = LocalArchiveExtraction(setOf(ZipArchiveExtractor()), UnexpectedLeftoverDiscarder)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `안쪽 폴더에서 고른 파일과 폴더만 그 폴더 기준 위치로 풀고 진행률은 고른 항목만 센다`() {
        val zip = zipOf("a.zip", "top.txt", "d/x.txt", "d/y.txt", "d/sub/z.txt")
        val phases = mutableListOf<ProgressPhase>()

        val result = extract(selectionOf(zip, listOf("d"), ArchiveEntryKey.Item(1), ArchiveEntryKey.Folder("sub"))) { phases += it }

        assertEquals(IoResult.Done, result)
        assertEquals("d/x.txt", File(dest, "x.txt").readText())
        assertEquals("d/sub/z.txt", File(dest, "sub/z.txt").readText())
        assertEquals(setOf("x.txt", "sub"), dest.list()!!.toSet())
        assertEquals(2, phases.filterIsInstance<ProgressPhase.Bytes>().last().fileCount)
    }

    @Test
    fun `고른 뒤 압축 파일의 목록이 바뀌었으면 아무것도 풀지 않고 실패한다`() {
        val zip = zipOf("a.zip", "a.txt", "b.txt")
        val selection = selectionOf(zip, emptyList(), ArchiveEntryKey.Item(0))
        zipOf("a.zip", "b.txt", "a.txt")

        val result = extract(selection)

        assertEquals(IoResult.ItemFailed(FailureReason.ArchiveChangedSinceViewed("a.zip")), result)
        assertEquals(0, dest.list()!!.size)
    }

    @Test
    fun `루트에서 고른 풀 수 없는 경로는 풀지 않고 실패로 알리며 나머지 고른 항목은 푼다`() {
        val zip = zipOf("a.zip", "../evil.txt", "ok.txt")

        val result = extract(selectionOf(zip, emptyList(), ArchiveEntryKey.Item(0), ArchiveEntryKey.Item(1)))

        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("../evil.txt", FailureReason.UnsafeArchiveEntryPath))), result)
        assertEquals("ok.txt", File(dest, "ok.txt").readText())
        assertFalse(File(dir, "evil.txt").exists())
    }

    @Test
    fun `고른 항목에 암호화된 것이 없으면 다른 항목이 암호화돼 있어도 비밀번호를 묻지 않는다`() {
        val zip = zipOf("a.zip", "plain.txt")
        ZipFile(zip, "secret".toCharArray()).use { zf ->
            zf.addFile(File(dir, "secret.txt").apply { writeText("hidden") }, ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            })
        }

        val result = extract(selectionOf(zip, emptyList(), ArchiveEntryKey.Item(0)))

        assertEquals(IoResult.Done, result)
        assertEquals("plain.txt", File(dest, "plain.txt").readText())
        assertFalse(File(dest, "secret.txt").exists())
    }

    /** [selection]을 [dest]에 묻지 않고 푼다(충돌·비밀번호 질문이 오면 테스트가 실패한다). */
    private fun extract(selection: ArchiveEntrySelection, onProgress: (ProgressPhase) -> Unit = {}): IoResult<Unit> = runBlocking {
        extraction.extractEntries(selection, dest.path, UnexpectedConflictResolver, UnexpectedPasswordProvider, onProgress)
    }

    /** 지금 [zip]의 목록으로 [folderNames] 폴더에서 [keys]를 고른 선택을 만든다 — 내용 보기가 목록을 읽고 고른 것과 같다. */
    private fun selectionOf(zip: File, folderNames: List<String>, vararg keys: ArchiveEntryKey): ArchiveEntrySelection {
        val entries = ZipArchiveExtractor().entries(zip.path, unexpectedPasswordSession())
        return ArchiveEntrySelection(zip.path, listingIdOf(entries), folderNames, keys.toSet())
    }

    /** 항목 이름을 내용으로 한 [entryNames]를 담은 [name] ZIP 파일을 목적지 밖에 만든다(있으면 새로 쓴다). */
    private fun zipOf(name: String, vararg entryNames: String): File =
        File(dir, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zos ->
                entryNames.forEach { entryName ->
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(entryName.toByteArray())
                    zos.closeEntry()
                }
            }
        }
}
