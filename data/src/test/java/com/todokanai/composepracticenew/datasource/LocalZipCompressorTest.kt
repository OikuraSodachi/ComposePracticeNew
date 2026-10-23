package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
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

/** [LocalZipCompressor]의 항목 이름 규칙(선택 항목 이름부터 시작), 빈 폴더 보존, 기존 파일을 덮어쓰지 않고 번호를 붙인 새 이름으로 만드는 동작을 검증하는 단위 테스트. */
class LocalZipCompressorTest {

    private val dir: File = Files.createTempDirectory("zip-test").toFile()
    private val compressor = LocalZipCompressor()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `같은 이름이 없으면 지정한 경로에 만든다`() = runBlocking {
        val src = folderWith("a.txt", "hello")

        assertEquals(IoResult.Done, compressor.zip(setOf(src.path), File(dir, "out.zip").path) {})

        assertEquals(listOf("src/", "src/a.txt"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `여러 파일을 직접 선택하면 각 파일 이름을 항목 이름으로 담는다`() = runBlocking {
        val a = File(dir, "a.txt").apply { writeText("hello") }
        val b = File(dir, "b.txt").apply { writeText("world") }

        assertEquals(IoResult.Done, compressor.zip(setOf(a.path, b.path), File(dir, "out.zip").path) {})

        val out = File(dir, "out.zip")
        assertEquals(listOf("a.txt", "b.txt"), entriesOf(out))
        ZipFile(out).use { zip -> assertEquals("world", zip.getInputStream(zip.getEntry("b.txt")).reader().readText()) }
    }

    @Test
    fun `폴더와 파일을 함께 선택하면 폴더 이름을 경로에 포함한다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        File(src, "sub").mkdirs(); File(src, "sub/c.txt").writeText("deep")
        val b = File(dir, "b.txt").apply { writeText("world") }

        assertEquals(IoResult.Done, compressor.zip(setOf(src.path, b.path), File(dir, "out.zip").path) {})

        assertEquals(listOf("b.txt", "src/", "src/a.txt", "src/sub/", "src/sub/c.txt"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `빈 폴더도 항목으로 담아 압축 해제 시 그대로 복원된다`() = runBlocking {
        val emptyAlone = File(dir, "empty").apply { mkdirs() }
        val src = folderWith("a.txt", "hello")
        File(src, "blank/deeper").mkdirs()

        assertEquals(IoResult.Done, compressor.zip(setOf(emptyAlone.path, src.path), File(dir, "out.zip").path) {})

        val out = File(dir, "out.zip")
        assertEquals(listOf("empty/", "src/", "src/a.txt", "src/blank/", "src/blank/deeper/"), entriesOf(out))
        val restored = File(dir, "restored").apply { mkdirs() }
        ZipArchiveExtractor().extract(out.path, restored, ByteProgressTracker(1, 1, onProgress = {}))
        assertTrue(File(restored, "empty").isDirectory)
        assertTrue(File(restored, "src/blank/deeper").isDirectory)
        assertEquals("hello", File(restored, "src/a.txt").readText())
    }

    @Test
    fun `빈 폴더만 선택해도 압축한다`() = runBlocking {
        val emptyAlone = File(dir, "empty").apply { mkdirs() }

        assertEquals(IoResult.Done, compressor.zip(setOf(emptyAlone.path), File(dir, "out.zip").path) {})

        assertEquals(listOf("empty/"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `같은 이름의 zip이 있으면 기존 파일은 그대로 두고 번호를 붙인 이름으로 만든다`() = runBlocking {
        val existing = File(dir, "photos.zip").apply { writeText("original") }
        val taken = File(dir, "photos (1).zip").apply { writeText("also taken") }
        val src = folderWith("a.txt", "hello")

        assertEquals(IoResult.Done, compressor.zip(setOf(src.path), existing.path) {})

        assertEquals("original", existing.readText())
        assertEquals("also taken", taken.readText())
        assertEquals(listOf("src/", "src/a.txt"), entriesOf(File(dir, "photos (2).zip")))
    }

    @Test
    fun `압축 대상 자신과 같은 이름이어도 원본을 잘라내지 않고 원본 내용을 그대로 담는다`() = runBlocking {
        val bytes = ByteArray(4096) { it.toByte() }
        val self = File(dir, "photos.zip").apply { writeBytes(bytes) }
        val a = File(dir, "a.txt").apply { writeText("hello") }

        assertEquals(IoResult.Done, compressor.zip(setOf(self.path, a.path), self.path) {})

        assertArrayEquals(bytes, self.readBytes())
        ZipFile(File(dir, "photos (1).zip")).use { zip ->
            assertArrayEquals(bytes, zip.getInputStream(zip.getEntry("photos.zip")).readBytes())
        }
    }

    /** [dir] 아래 src 폴더에 [name] 파일을 [content] 내용으로 만들고 그 폴더를 반환한다. */
    private fun folderWith(name: String, content: String): File =
        File(dir, "src").apply { mkdirs(); File(this, name).writeText(content) }

    /** [zip] 안의 항목 이름 목록을 정렬해 반환한다. */
    private fun entriesOf(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().toList().map { it.name }.sorted() }
}
