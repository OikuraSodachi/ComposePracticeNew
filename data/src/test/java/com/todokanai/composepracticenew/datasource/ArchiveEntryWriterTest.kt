package com.todokanai.composepracticenew.datasource

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** [ArchiveEntryWriter]가 폴더·파일 항목을 루트 아래에 만들고, 만들지 못하면 알리며, 루트 밖 항목만 건너뜀으로 세는지 검증하는 단위 테스트(#235, #317). */
class ArchiveEntryWriterTest {

    /** 추출 루트를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("archive-entry-writer-test").toFile()
    /** 추출 루트. */
    private val root = File(dir, "out").also { it.mkdirs() }
    /** 검증 대상. */
    private val writer = ArchiveEntryWriter(ExtractionRoot(root))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `폴더 항목은 빈 디렉터리로 만든다`() {
        writer.createFolder("empty/")
        writer.createFolder("a/b")

        assertTrue(File(root, "empty").isDirectory)
        assertTrue(File(root, "a/b").isDirectory)
    }

    @Test
    fun `같은 이름의 파일이 있어 폴더를 만들지 못하면 IOException으로 알린다`() {
        File(root, "x").writeText("file")

        assertThrows(IOException::class.java) { writer.createFolder("x/") }
    }

    @Test
    fun `파일 항목은 부모 디렉터리를 만들고 그 위치에 쓴다`() {
        writer.openFile("d/e/a.txt")!!.use { it.write("hi".toByteArray()) }

        assertEquals("hi", File(root, "d/e/a.txt").readText())
    }

    @Test
    fun `부모 위치에 파일이 있어 파일 항목을 만들지 못하면 IOException으로 알린다`() {
        File(root, "p").writeText("file")

        assertThrows(IOException::class.java) { writer.openFile("p/a.txt") }
    }

    @Test
    fun `루트 밖이나 루트 자신을 가리키는 항목만 건너뜀으로 센다`() {
        assertNull(writer.openFile("../evil.txt"))
        writer.createFolder("../evil/")
        writer.createFolder("./")

        assertEquals(3, writer.skippedEntries)
        assertEquals(listOf("out"), dir.list()!!.toList())
    }
}
