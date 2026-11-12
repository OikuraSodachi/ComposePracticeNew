package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.FailedItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [ArchiveEntryWriter]가 폴더·파일 항목을 루트 아래에 만들고, 만들지 못하면 알리며, 루트 밖 항목만 실패 항목으로 남기는지 검증하는 단위 테스트(#235, #317). */
class ArchiveEntryWriterTest {

    /** 추출 루트를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("archive-entry-writer-test").toFile()
    /** 추출 루트. */
    private val root = File(dir, "out").also { it.mkdirs() }
    /** 검증 대상. */
    private val writer = ArchiveEntryWriter(ExtractionRoot(root), UnexpectedConflictAsk, ByteProgressTracker(1, 1, onProgress = {}))

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
    fun `파일 항목은 부모 디렉터리를 만들고 그 위치에 쓴다`() {
        writer.openFile("d/e/a.txt", null)!!.use { it.write("hi".toByteArray()) }

        assertEquals("hi", File(root, "d/e/a.txt").readText())
    }

    @Test
    fun `루트 밖이나 루트 자신을 가리키는 항목만 그 이름으로 실패 항목에 남긴다`() {
        assertNull(writer.openFile("../evil.txt", null))
        writer.createFolder("../evil/")
        writer.createFolder("./")

        assertEquals(listOf("../evil.txt", "../evil/", "./").map { FailedItem(it, ArchiveEntryWriter.REJECTED_PATH_REASON) }, writer.failures)
        assertEquals(listOf("out"), dir.list()!!.toList())
    }
}
