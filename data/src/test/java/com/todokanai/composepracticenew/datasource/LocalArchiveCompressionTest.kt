package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.testviews.*
import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ByteAmount
import com.todokanai.composepracticenew.model.ProgressPhase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * [LocalArchiveCompression]과 [ZipArchiveCompressor]의 압축 동작을 검증하는 단위 테스트.
 * 항목 이름 규칙(선택 항목 이름부터 시작), 빈 폴더 보존, 기존 파일을 덮어쓰지 않고 번호를 붙인 새 이름으로 만드는 동작, 구현이 없는 형식의 실패를 확인한다.
 * 기록용 구현으로 조율부만 떼어, 형식별 구현에 넘기는 항목·출력 파일·폴더 우선 순서, 진행 시작·완료 시점, 쓰기 도중 실패의 결과도 확인한다.
 */
class LocalArchiveCompressionTest {

    private val dir: File = Files.createTempDirectory("zip-test").toFile()
    private val compression = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to ZipArchiveCompressor()), UnexpectedLeftoverDiscarder)

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
        val tracker = ByteProgressTracker(1, 1, onProgress = {})
        ZipArchiveExtractor().extract(out.path, ArchiveEntryWriter(ExtractionRoot(restored), UnexpectedConflictAsk, tracker, UnexpectedLeftoverDiscarder), unexpectedPasswordSession(), tracker)
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

        val result = LocalArchiveCompression(emptyMap(), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {}

        assertTrue(result.toString(), result is IoResult.ItemFailed)
        assertTrue(!File(dir, "out.zip").exists())
    }

    @Test
    fun `지원 형식은 구현을 등록한 형식이다`() {
        assertEquals(setOf(ArchiveFormat.ZIP), compression.supportedFormats())
        assertEquals(emptySet<ArchiveFormat>(), LocalArchiveCompression(emptyMap(), UnexpectedLeftoverDiscarder).supportedFormats())
    }

    @Test
    fun `형식별 구현에는 모을 때 정한 폴더·파일 구분과 파일 크기를 담아 넘긴다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        val recorder = RecordingCompressor()

        assertEquals(IoResult.Done, LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to recorder), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {})

        assertEquals(listOf(ArchiveSource.Folder("src"), ArchiveSource.Regular("src/a.txt", File(src, "a.txt"), 5L)), recorder.entries)
        assertEquals(File(dir, "out.zip"), recorder.output)
    }

    @Test
    fun `형식별 구현에는 폴더 항목을 그 하위 항목보다 먼저 넘긴다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        File(src, "sub/deeper").mkdirs()
        File(src, "sub/c.txt").writeText("deep")
        File(src, "sub/deeper/d.txt").writeText("d")
        val recorder = RecordingCompressor()

        assertEquals(IoResult.Done, LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to recorder), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {})

        val names = recorder.entries.map { it.name }
        assertEquals(setOf("src", "src/a.txt", "src/sub", "src/sub/c.txt", "src/sub/deeper", "src/sub/deeper/d.txt"), names.toSet())
        recorder.entries.forEach { entry ->
            val parent = entry.name.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) assertTrue("$parent 이(가) ${entry.name}보다 먼저 와야 한다: $names", names.indexOf(parent) in 0 until names.indexOf(entry.name))
        }
    }

    @Test
    fun `진행 시작은 형식별 쓰기 전에, 완료는 쓰기 뒤에 알린다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        val progress = mutableListOf<ProgressPhase>()
        var reportedBeforeWrite = -1
        val recorder = RecordingCompressor { reportedBeforeWrite = progress.size }

        assertEquals(IoResult.Done, LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to recorder), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) { progress += it })

        assertEquals(1, reportedBeforeWrite)
        assertEquals(2, progress.size)
        assertEquals(
            ProgressPhase.Bytes(ByteAmount.Known(0, 5), writtenBytes = 0, fileIndex = 0, fileCount = 1, currentFile = null),
            progress.first()
        )
        assertEquals(100, progress.last().asKnown().percent)
    }

    @Test
    fun `형식별 구현이 쓰는 도중 실패하면 예외를 던지지 않고 실패를 돌려준다`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        val failing = RecordingCompressor { throw IOException("쓰기 실패") }

        val result = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to failing), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {}

        assertEquals(IoResult.ItemFailed(FailureReason.IoFailed("쓰기 실패")), result)
    }

    @Test
    fun `ZIP을 쓰다 원본을 읽지 못하면 앞 항목까지만 담겨 정상으로 열리는 아카이브를 남기지 않는다(#427)`() = runBlocking {
        val src = folderWith("a.txt", "hello")
        val gone = File(src, "b.txt").apply { writeText("world") }
        // 항목을 모은 뒤 쓰기 직전에 원본 하나를 없애고 그 항목을 맨 뒤로 보내, a.txt를 쓴 다음 항목에서 읽기가 실패하게 한다.
        // 폴더 안 형제 순서(listFiles)는 정해져 있지 않아 테스트가 순서를 정한다 — 안정 정렬이라 폴더 항목은 맨 앞에 남는다
        val vanishing = object : ArchiveCompressor {
            override fun write(entries: List<ArchiveSource>, output: File, password: String?, tracker: ByteProgressTracker) {
                gone.delete()
                ZipArchiveCompressor().write(entries.sortedBy { it is ArchiveSource.Regular && it.file == gone }, output, password, tracker)
            }
        }

        val result = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to vanishing), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {}

        assertTrue("$result", result is IoResult.ItemFailed)
        assertFalse(File(dir, "out.zip").exists())
    }

    @Test
    fun `번호를 붙인 이름으로 만들다 실패하면 이 압축이 만든 아카이브만 지우고 기존 파일은 그대로 둔다(#427)`() = runBlocking {
        val existing = File(dir, "out.zip").apply { writeText("original") }
        val src = folderWith("a.txt", "hello")
        val failing = RecordingCompressor { throw IOException("쓰기 실패") }

        LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to failing), UnexpectedLeftoverDiscarder).compress(setOf(src.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {}

        assertEquals(File(dir, "out (1).zip"), failing.output)
        assertFalse(File(dir, "out (1).zip").exists())
        assertEquals("original", existing.readText())
    }

    /**
     * 받은 항목과 출력 파일을 기록하고, 받은 뒤 [onWrite]를 실행하는 테스트용 ArchiveCompressor.
     * @param onWrite 기록한 뒤 실행할 동작 — 쓰기 시점 확인이나 실패 흉내에 쓴다
     */
    private class RecordingCompressor(private val onWrite: () -> Unit = {}) : ArchiveCompressor {
        /** 마지막 [write] 호출이 받은 항목. */
        var entries: List<ArchiveSource> = emptyList()
        /** 마지막 [write] 호출이 받은 출력 파일. */
        var output: File? = null

        override fun write(entries: List<ArchiveSource>, output: File, password: String?, tracker: ByteProgressTracker) {
            this.entries = entries
            this.output = output
            onWrite()
        }
    }

    /** [targetFiles]를 [dir] 아래 [baseName].zip으로 압축한다. */
    private suspend fun compress(targetFiles: Set<String>, baseName: String): IoResult<Unit> =
        compression.compress(targetFiles, dir.path, baseName, ArchiveFormat.ZIP, password = null) {}

    /** [dir] 아래 src 폴더에 [name] 파일을 [content] 내용으로 만들고 그 폴더를 반환한다. */
    private fun folderWith(name: String, content: String): File =
        File(dir, "src").apply { mkdirs(); File(this, name).writeText(content) }

    /** [zip] 안의 항목 이름 목록을 정렬해 반환한다. */
    private fun entriesOf(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().toList().map { it.name }.sorted() }
}
