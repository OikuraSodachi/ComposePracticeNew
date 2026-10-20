package com.todokanai.composepracticenew.datasource

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** [LocalZipCompressor]가 기존 파일을 덮어쓰지 않고 번호를 붙인 새 이름으로 압축 파일을 만드는지 검증하는 단위 테스트. */
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

        compressor.zip(setOf(src.path), File(dir, "out.zip").path) {}

        assertEquals(listOf("a.txt"), entriesOf(File(dir, "out.zip")))
    }

    @Test
    fun `같은 이름의 zip이 있으면 기존 파일은 그대로 두고 번호를 붙인 이름으로 만든다`() = runBlocking {
        val existing = File(dir, "photos.zip").apply { writeText("original") }
        val taken = File(dir, "photos (1).zip").apply { writeText("also taken") }
        val src = folderWith("a.txt", "hello")

        compressor.zip(setOf(src.path), existing.path) {}

        assertEquals("original", existing.readText())
        assertEquals("also taken", taken.readText())
        assertEquals(listOf("a.txt"), entriesOf(File(dir, "photos (2).zip")))
    }

    @Test
    fun `압축 대상 자신과 같은 이름이어도 원본을 잘라내지 않는다`() = runBlocking {
        val bytes = ByteArray(4096) { it.toByte() }
        val self = File(dir, "photos.zip").apply { writeBytes(bytes) }

        compressor.zip(setOf(self.path), self.path) {}

        assertArrayEquals(bytes, self.readBytes())
        val created = File(dir, "photos (1).zip")
        assertTrue(created.exists() && created.length() > 0)
    }

    /** [dir] 아래 src 폴더에 [name] 파일을 [content] 내용으로 만들고 그 폴더를 반환한다. */
    private fun folderWith(name: String, content: String): File =
        File(dir, "src").apply { mkdirs(); File(this, name).writeText(content) }

    /** [zip] 안의 항목 이름 목록을 정렬해 반환한다. */
    private fun entriesOf(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().toList().map { it.name }.sorted() }
}
