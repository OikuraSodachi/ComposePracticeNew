package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 로컬 복사·이동이 같은 이름의 항목을 쓰는 시점에 파일 시스템 결과로 판정해 묻고, 답대로 덮어쓰기·합치기·건너뛰기를 하는지 검증하는 단위 테스트(#316).
 * 이동은 같은 파티션(rename)과 교차 파티션(copy+delete) 두 경로를 모두 확인한다.
 */
class LocalTreeTransferConflictTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val src by lazy { temp.newFolder("src") }
    private val dest by lazy { temp.newFolder("dest") }

    @Test
    fun `같은 이름이 없으면 묻지 않는다`() {
        File(src, "a.txt").writeText("new")
        val resolver = AnsweringResolver()

        assertEquals(IoResult.Done, copy(resolver, "a.txt"))

        assertEquals(emptyList<WriteConflict>(), resolver.asked)
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun `복사에서 같은 이름의 파일을 건너뛰면 기존 파일을 그대로 둔다`() {
        File(src, "a.txt").writeText("new")
        File(dest, "a.txt").writeText("old")
        val resolver = AnsweringResolver { ConflictChoice.SKIP }

        assertEquals(IoResult.Done, copy(resolver, "a.txt"))

        assertEquals(listOf(WriteConflict("a.txt", dest.path, WriteConflictKind.FILE)), resolver.asked)
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `복사에서 같은 이름의 파일을 덮어쓰면 새 내용으로 바뀐다`() {
        File(src, "a.txt").writeText("new")
        File(dest, "a.txt").writeText("older content")

        assertEquals(IoResult.Done, copy(AnsweringResolver { ConflictChoice.PROCEED }, "a.txt"))

        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun `원본을 열지 못하면 묻지 않고 실패하며 같은 이름의 기존 파일을 비우지 않는다`() {
        File(dest, "a.txt").writeText("old")

        assertTrue(copy(AnsweringResolver(), "a.txt") !is IoResult.Success)

        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `복사에서 폴더를 합치면 안쪽 항목을 하나씩 다시 묻는다`() {
        File(src, "dir").mkdir()
        File(src, "dir/same.txt").writeText("new")
        File(src, "dir/fresh.txt").writeText("fresh")
        File(dest, "dir").mkdir()
        File(dest, "dir/same.txt").writeText("old")
        File(dest, "dir/kept.txt").writeText("kept")
        val resolver = AnsweringResolver { if (it.kind == WriteConflictKind.FOLDER) ConflictChoice.PROCEED else ConflictChoice.SKIP }

        assertEquals(IoResult.Done, copy(resolver, "dir"))

        assertEquals(
            listOf(WriteConflictKind.FOLDER to "dir", WriteConflictKind.FILE to "same.txt"),
            resolver.asked.map { it.kind to it.name }
        )
        assertEquals("old", File(dest, "dir/same.txt").readText())
        assertEquals("fresh", File(dest, "dir/fresh.txt").readText())
        assertEquals("kept", File(dest, "dir/kept.txt").readText())
    }

    @Test
    fun `복사에서 폴더를 건너뛰면 안쪽은 묻지 않고 쓰지 않는다`() {
        File(src, "dir").mkdir()
        File(src, "dir/same.txt").writeText("new")
        File(dest, "dir").mkdir()
        File(dest, "dir/same.txt").writeText("old")
        val resolver = AnsweringResolver { ConflictChoice.SKIP }

        assertEquals(IoResult.Done, copy(resolver, "dir"))

        assertEquals(1, resolver.asked.size)
        assertEquals("old", File(dest, "dir/same.txt").readText())
    }

    @Test
    fun `파일 자리에 폴더가 있으면 종류가 엇갈린 겹침으로 묻는다`() {
        // POSIX(Android)는 폴더 자리의 CREATE_NEW를 '이미 있음'으로 알리지만, Windows는 권한 거부로 알린다 — 앱의 대상(POSIX)에서만 확인한다
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        File(src, "a").writeText("file")
        File(dest, "a").mkdir()
        val resolver = AnsweringResolver { ConflictChoice.SKIP }

        assertEquals(IoResult.Done, copy(resolver, "a"))

        assertEquals(listOf(WriteConflictKind.TYPE_MISMATCH), resolver.asked.map { it.kind })
        assertTrue(File(dest, "a").isDirectory)
    }

    @Test
    fun `같은 파티션 이동에서 건너뛴 파일은 원래 자리에 남고 기존 파일도 그대로다`() {
        File(src, "a.txt").writeText("new")
        File(dest, "a.txt").writeText("old")

        assertEquals(IoResult.Done, move(AnsweringResolver { ConflictChoice.SKIP }, samePartition = true, "a.txt"))

        assertEquals("new", File(src, "a.txt").readText())
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `같은 파티션 이동에서 덮어쓰면 원본이 기존 파일을 대신한다`() {
        File(src, "a.txt").writeText("new")
        File(dest, "a.txt").writeText("old")

        assertEquals(IoResult.Done, move(AnsweringResolver { ConflictChoice.PROCEED }, samePartition = true, "a.txt"))

        assertFalse(File(src, "a.txt").exists())
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun `같은 파티션 이동에서 겹치지 않는 파일은 묻지 않고 옮기며 자리 확보용 빈 파일을 남기지 않는다`() {
        File(src, "a.txt").writeText("new")
        val resolver = AnsweringResolver()

        assertEquals(IoResult.Done, move(resolver, samePartition = true, "a.txt"))

        assertEquals(emptyList<WriteConflict>(), resolver.asked)
        assertFalse(File(src, "a.txt").exists())
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun `같은 파티션 이동에서 폴더를 합치고 일부를 건너뛰면 건너뛴 항목만 원본 폴더에 남는다`() {
        mergeScenario()

        assertEquals(IoResult.Done, move(mergeSkippingSame(), samePartition = true, "dir"))

        assertMergedSkippingSame()
    }

    @Test
    fun `교차 파티션 이동에서 폴더를 합치고 일부를 건너뛰면 건너뛴 항목만 원본 폴더에 남는다`() {
        mergeScenario()

        assertEquals(IoResult.Done, move(mergeSkippingSame(), samePartition = false, "dir"))

        assertMergedSkippingSame()
    }

    @Test
    fun `교차 파티션 이동에서 폴더를 모두 합치면 원본 폴더를 지운다`() {
        mergeScenario()

        assertEquals(IoResult.Done, move(AnsweringResolver { ConflictChoice.PROCEED }, samePartition = false, "dir"))

        assertFalse(File(src, "dir").exists())
        assertEquals("new", File(dest, "dir/same.txt").readText())
        assertEquals("fresh", File(dest, "dir/fresh.txt").readText())
    }

    /** src/dir(same.txt, fresh.txt)과 dest/dir(same.txt)을 만든다. */
    private fun mergeScenario() {
        File(src, "dir").mkdir()
        File(src, "dir/same.txt").writeText("new")
        File(src, "dir/fresh.txt").writeText("fresh")
        File(dest, "dir").mkdir()
        File(dest, "dir/same.txt").writeText("old")
    }

    /** 폴더는 합치고 파일 겹침은 건너뛰는 답. */
    private fun mergeSkippingSame() =
        AnsweringResolver { if (it.kind == WriteConflictKind.FOLDER) ConflictChoice.PROCEED else ConflictChoice.SKIP }

    /** [mergeScenario]를 [mergeSkippingSame]로 이동한 결과를 확인한다. */
    private fun assertMergedSkippingSame() {
        assertEquals(listOf("same.txt"), File(src, "dir").list()!!.toList())
        assertEquals("new", File(src, "dir/same.txt").readText())
        assertEquals("old", File(dest, "dir/same.txt").readText())
        assertEquals("fresh", File(dest, "dir/fresh.txt").readText())
    }

    /** src의 [names] 항목을 dest로 복사한다. */
    private fun copy(resolver: WriteConflictResolver, vararg names: String): IoResult<Unit> = runBlocking {
        transfer(samePartition = false).copy(names.mapTo(LinkedHashSet()) { File(src, it).path }, dest.path, resolver) {}
    }

    /** src의 [names] 항목을 dest로 이동한다. */
    private fun move(resolver: WriteConflictResolver, samePartition: Boolean, vararg names: String): IoResult<Unit> = runBlocking {
        transfer(samePartition).move(names.mapTo(LinkedHashSet()) { File(src, it).path }, dest.path, resolver) {}
    }

    /** 이동 경로를 [samePartition]으로 고르는 transfer. */
    private fun transfer(samePartition: Boolean) = LocalTreeTransfer(isSamePartition = { _, _ -> samePartition }, freeSpaceOf = { Long.MAX_VALUE })

    /**
     * 받은 겹침을 기록하고 [answer]로 답하는 resolver.
     * @param answer 겹침마다 돌려줄 처리 방법. 기본은 묻지 않아야 하는 경우라 실패시킨다
     */
    private class AnsweringResolver(
        private val answer: (WriteConflict) -> ConflictChoice = { throw AssertionError("예상하지 못한 충돌: $it") }
    ) : WriteConflictResolver {
        /** 받은 겹침(받은 순서). */
        val asked = mutableListOf<WriteConflict>()

        override suspend fun resolve(conflict: WriteConflict): ConflictChoice {
            asked += conflict
            return answer(conflict)
        }
    }
}
