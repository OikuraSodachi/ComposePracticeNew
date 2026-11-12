package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 압축 해제가 항목마다 쓰는 시점에 같은 이름을 판정하는지 검증하는 단위 테스트(#316).
 * 폴더는 묻지 않고 합치고, 파일 겹침과 종류가 엇갈린 겹침만 묻는다. 묻고 건너뛴 항목은 경로 검증 실패로 세지 않는다.
 */
class ArchiveEntryWriterConflictTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val root by lazy { temp.newFolder("root").canonicalFile }

    /** 받은 겹침(받은 순서). */
    private val asked = mutableListOf<WriteConflict>()

    @Test
    fun `이미 있는 폴더는 묻지 않고 합친다`() {
        File(root, "dir").mkdir()
        File(root, "dir/kept.txt").writeText("kept")

        writer { throw AssertionError("예상하지 못한 충돌: $it") }.createFolder("dir/")

        assertEquals("kept", File(root, "dir/kept.txt").readText())
    }

    @Test
    fun `같은 이름의 파일을 건너뛰면 열지 않고 건너뜀으로 세지 않는다`() {
        File(root, "a.txt").writeText("old")
        val writer = writer { ConflictChoice.SKIP }

        assertNull(writer.openFile("a.txt"))

        assertEquals(listOf(WriteConflict("a.txt", root.path, WriteConflictKind.FILE)), asked)
        assertEquals(0, writer.skippedEntries)
        assertEquals("old", File(root, "a.txt").readText())
    }

    @Test
    fun `같은 이름의 파일을 덮어쓰면 비우고 연다`() {
        File(root, "a.txt").writeText("older content")

        writer { ConflictChoice.PROCEED }.openFile("a.txt")!!.use { it.write("new".toByteArray()) }

        assertEquals("new", File(root, "a.txt").readText())
    }

    @Test
    fun `폴더 자리에 파일이 있으면 한 번만 묻고 그 아래 항목은 묻지 않고 건너뛴다`() {
        File(root, "dir").writeText("file in the way")
        val writer = writer { ConflictChoice.SKIP }

        assertNull(writer.openFile("dir/a.txt"))
        assertNull(writer.openFile("dir/sub/b.txt"))
        writer.createFolder("dir/sub/")

        assertEquals(listOf(WriteConflictKind.TYPE_MISMATCH), asked.map { it.kind })
        assertEquals(0, writer.skippedEntries)
        assertTrue(File(root, "dir").isFile)
    }

    /** [answer]로 답하며 받은 겹침을 [asked]에 기록하는 writer. */
    private fun writer(answer: (WriteConflict) -> ConflictChoice) =
        ArchiveEntryWriter(ExtractionRoot(root)) { conflict -> asked += conflict; answer(conflict) }
}
