package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.ProgressState
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
 * 폴더는 묻지 않고 합치고, 파일 겹침과 종류가 엇갈린 겹침만 묻는다. 묻고 건너뛴 항목은 경로 검증 실패로 세지 않되, 진행률에서는 처리한 것으로 넘긴다.
 */
class ArchiveEntryWriterConflictTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val root by lazy { temp.newFolder("root").canonicalFile }

    /** 받은 겹침(받은 순서). */
    private val asked = mutableListOf<WriteConflict>()

    /** writer가 넘긴 진행 상태(받은 순서). */
    private val reports = mutableListOf<ProgressState>()

    /** 파일 두 개, 6바이트짜리 작업의 진행률 보고기. */
    private val tracker = ByteProgressTracker(6, 2, onProgress = { reports += it })

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

        assertNull(writer.openFile("a.txt", null))

        assertEquals(listOf(WriteConflict("a.txt", root.path, WriteConflictKind.FILE)), asked)
        assertEquals(0, writer.skippedEntries)
        assertEquals("old", File(root, "a.txt").readText())
    }

    @Test
    fun `같은 이름의 파일을 덮어쓰면 비우고 연다`() {
        File(root, "a.txt").writeText("older content")

        writer { ConflictChoice.PROCEED }.openFile("a.txt", null)!!.use { it.write("new".toByteArray()) }

        assertEquals("new", File(root, "a.txt").readText())
    }

    @Test
    fun `폴더 자리에 파일이 있으면 한 번만 묻고 그 아래 항목은 묻지 않고 건너뛴다`() {
        File(root, "dir").writeText("file in the way")
        val writer = writer { ConflictChoice.SKIP }

        assertNull(writer.openFile("dir/a.txt", null))
        assertNull(writer.openFile("dir/sub/b.txt", null))
        writer.createFolder("dir/sub/")

        assertEquals(listOf(WriteConflictKind.TYPE_MISMATCH), asked.map { it.kind })
        assertEquals(0, writer.skippedEntries)
        assertTrue(File(root, "dir").isFile)
    }

    @Test
    fun `쓰지 않은 파일 항목은 묻고 건너뛰었든 루트 밖이든 진행률에서 처리한 것으로 넘긴다`() {
        File(root, "a.txt").writeText("old")
        val writer = writer { ConflictChoice.SKIP }

        assertNull(writer.openFile("a.txt", 4))
        assertNull(writer.openFile("../evil.txt", 2))

        assertEquals(2, reports.last().currentIndex)
        assertEquals(6L, reports.last().writtenBytes)
    }

    /** [answer]로 답하며 받은 겹침을 [asked]에 기록하는 writer. */
    private fun writer(answer: (WriteConflict) -> ConflictChoice) =
        ArchiveEntryWriter(ExtractionRoot(root), { conflict -> asked += conflict; answer(conflict) }, tracker)
}
