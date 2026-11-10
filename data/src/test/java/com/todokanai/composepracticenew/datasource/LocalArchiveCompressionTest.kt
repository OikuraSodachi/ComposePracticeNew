package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * [LocalArchiveCompression]과 [ZipArchiveCompressor]의 압축 동작을 검증하는 단위 테스트.
 * 항목 이름 규칙(선택 항목 이름부터 시작), 빈 폴더 보존, 기존 파일을 덮어쓰지 않고 번호를 붙인 새 이름으로 만드는 동작, 구현이 없는 형식의 실패를 확인한다.
 */
class LocalArchiveCompressionTest {

    private val dir: File = Files.createTempDirectory("zip-test").toFile()
    private val compression = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to ZipArchiveCompressor()))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `같은 이름이 없으면 받은 이름에 형식의 확장자를 붙여 만든다`() = runBlocking {
        val src = folderWith("a.txt", "hello")

        assertEquals(IoResult.Done, compress(setOf(src.path), "out"))

        assertEquals(listOf("src/", "src/a.txt"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `여러 파일을 직접 선택하면 각 파일 이름을 항목 이름으로 담는다`() = runBlocking {
        val a = File(dir, "a.txt").apply { writeText("hello") }
        val b = File(dir, "b.txt").apply { writeText("world") }

        assertEquals(IoResult.Done, compress(setOf(a.path, b.path), "out"))

        val out = File(dir, "out.zip")
        assertEquals(listOf("a.txt", "b.txt"), entriesOf(out))
        ZipFile(out).use { zip -> assertEquals("world", zip.getInputStream(zip.getEntry("b.txt")).reader().readText()) }
    }

    @Test
    fun `폴더와 파일을 함께 선택하면 폴더 이름을 경로에 포함한다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        File(src, "sub").mkdirs(); File(src, "sub/c.txt").writeText("deep")
        val b = File(dir, "b.txt").apply { writeText("world") }

        assertEquals(IoResult.Done, compress(setOf(src.path, b.path), "out"))

        assertEquals(listOf("b.txt", "src/", "src/a.txt", "src/sub/", "src/sub/c.txt"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `빈 폴더도 항목으로 담아 압축 해제 시 그대로 복원된다`() = runBlocking {
        val emptyAlone = File(dir, "empty").apply { mkdirs() }
        val src = folderWith("a.txt", "hello")
        File(src, "blank/deeper").mkdirs()

        assertEquals(IoResult.Done, compress(setOf(emptyAlone.path, src.path), "out"))

        val out = File(dir, "out.zip")
        assertEquals(listOf("empty/", "src/", "src/a.txt", "src/blank/", "src/blank/deeper/"), entriesOf(out))
        val restored = File(dir, "restored").apply { mkdirs() }
        ZipArchiveExtractor().extract(out.path, ExtractionRoot(restored), ByteProgressTracker(1, 1, onProgress = {}))
        assertTrue(File(restored, "empty").isDirectory)
        assertTrue(File(restored, "src/blank/deeper").isDirectory)
        assertEquals("hello", File(restored, "src/a.txt").readText())
    }

    @Test
    fun `빈 폴더만 선택해도 압축한다`() = runBlocking {
        val emptyAlone = File(dir, "empty").apply { mkdirs() }

        assertEquals(IoResult.Done, compress(setOf(emptyAlone.path), "out"))

        assertEquals(listOf("empty/"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `같은 이름의 zip이 있으면 기존 파일은 그대로 두고 번호를 붙인 이름으로 만든다`() = runBlocking {
        val existing = File(dir, "photos.zip").apply { writeText("original") }
        val taken = File(dir, "photos (1).zip").apply { writeText("also taken") }
        val src = folderWith("a.txt", "hello")

        assertEquals(IoResult.Done, compress(setOf(src.path), "photos"))

        assertEquals("original", existing.readText())
        assertEquals("also taken", taken.readText())
        assertEquals(listOf("src/", "src/a.txt"), entriesOf(File(dir, "photos (2).zip")))
    }

    @Test
    fun `압축 대상 자신과 같은 이름이어도 원본을 잘라내지 않고 원본 내용을 그대로 담는다`() = runBlocking {
        val bytes = ByteArray(4096) { it.toByte() }
        val self = File(dir, "photos.zip").apply { writeBytes(bytes) }
        val a = File(dir, "a.txt").apply { writeText("hello") }

        assertEquals(IoResult.Done, compress(setOf(self.path, a.path), "photos"))

        assertArrayEquals(bytes, self.readBytes())
        ZipFile(File(dir, "photos (1).zip")).use { zip ->
            assertArrayEquals(bytes, zip.getInputStream(zip.getEntry("photos.zip")).readBytes())
        }
    }

    @Test
    fun `형식을 쓰는 구현이 없으면 아무것도 만들지 않고 실패한다`() = runBlocking {
        val src = folderWith("a.txt", "hello")

        val result = LocalArchiveCompression(emptyMap()).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP) {}

        assertTrue(result.toString(), result is IoResult.ItemFailed)
        assertTrue(!File(dir, "out.zip").exists())
    }

    @Test
    fun `지원 형식은 구현을 등록한 형식이다`() {
        assertEquals(setOf(ArchiveFormat.ZIP), compression.supportedFormats())
        assertEquals(emptySet<ArchiveFormat>(), LocalArchiveCompression(emptyMap()).supportedFormats())
    }

    @Test
    fun `형식별 구현에는 모을 때 정한 폴더·파일 구분과 파일 크기를 담아 넘긴다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        val recorder = RecordingCompressor()

        assertEquals(IoResult.Done, LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to recorder)).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP) {})

        assertEquals(listOf(ArchiveSource.Folder("src"), ArchiveSource.Regular("src/a.txt", File(src, "a.txt"), 5L)), recorder.entries)
    }

    /** 받은 항목을 기록하기만 하는 테스트용 ArchiveCompressor. */
    private class RecordingCompressor : ArchiveCompressor {
        /** 마지막 [write] 호출이 받은 항목. */
        var entries: List<ArchiveSource> = emptyList()

        override fun write(entries: List<ArchiveSource>, output: File, tracker: ByteProgressTracker) {
            this.entries = entries
        }
    }

    /** [targetFiles]를 [dir] 아래 [baseName].zip으로 압축한다. */
    private suspend fun compress(targetFiles: Set<String>, baseName: String): IoResult<Unit> =
        compression.compress(targetFiles, dir.path, baseName, ArchiveFormat.ZIP) {}

    /** [dir] 아래 src 폴더에 [name] 파일을 [content] 내용으로 만들고 그 폴더를 반환한다. */
    private fun folderWith(name: String, content: String): File =
        File(dir, "src").apply { mkdirs(); File(this, name).writeText(content) }

    /** [zip] 안의 항목 이름 목록을 정렬해 반환한다. */
    private fun entriesOf(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().toList().map { it.name }.sorted() }
}
