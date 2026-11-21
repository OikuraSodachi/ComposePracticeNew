package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * [LocalTreeTransfer]가 여유 공간 부족과 항목별 입출력 실패를 결과로 알리고, 실패한 항목만 쓰지 않거나 원본에 남기며 나머지는 처리하는지 검증하는 단위 테스트.
 * 파티션 판별과 여유 공간은 OS에 따라 달라 테스트가 정한다.
 */
class LocalTreeTransferTest {

    /** 원본과 대상을 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("local-tree-transfer-test").toFile()
    /** 옮길 원본 폴더. */
    private val source = File(dir, "src").apply { mkdirs(); File(this, "a.txt").writeText("AAAA") }
    /** 옮겨 갈 대상 디렉터리. */
    private val target = File(dir, "dst").apply { mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `복사할 크기보다 여유 공간이 작으면 아무것도 복사하지 않고 공간 부족으로 실패한다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { 1L }, syncToDisk = {})

        val result = runBlocking { transfer.copy(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(1L, ((result as IoResult.ItemFailed).reason as FailureReason.NotEnoughSpace).freeBytes)
        assertFalse(File(target, "src").exists())
    }

    @Test
    fun `다른 파티션으로 옮길 때 대상에 여유 공간이 없으면 원본을 그대로 두고 실패한다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { 0L }, syncToDisk = {})

        val result = runBlocking { transfer.move(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(IoResult.ItemFailed(FailureReason.NoFreeSpace), result)
        assertTrue(File(source, "a.txt").exists())
        assertFalse(File(target, "src").exists())
    }

    @Test
    fun `다른 파티션으로 옮길 때 여유 공간이 있지만 모자라면 원본을 그대로 두고 필요한 공간과 함께 실패한다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { 1L }, syncToDisk = {})

        val result = runBlocking { transfer.move(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(1L, ((result as IoResult.ItemFailed).reason as FailureReason.NotEnoughSpace).freeBytes)
        assertTrue(File(source, "a.txt").exists())
        assertFalse(File(target, "src").exists())
    }

    @Test
    fun `다른 파티션 이동에서 한 항목이 입출력 실패로 멈추면 그 항목을 알리고 원본을 남기며 나머지 항목은 옮긴다(#504)`() {
        // 같은 이름의 파일이 대상에 있어 겹침을 묻게 하고, 묻는 쪽이 입출력 실패를 던져 첫 항목만 실패시킨다
        File(target, "src").writeText("file")
        val other = File(dir, "b.txt").apply { writeText("B") }
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.move(linkedSetOf(source.path, other.path), target.path, failingResolver) {} }

        assertEquals(listOf("src"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertTrue("원본이 남아야 함", File(source, "a.txt").exists())
        assertEquals("B", File(target, "b.txt").readText())
        assertFalse(other.exists())
    }

    @Test
    fun `같은 파티션 이동이 입출력 실패로 멈추면 그 항목을 알리고 원본을 남긴다`() {
        File(target, "src").apply { mkdirs(); File(this, "b.txt").writeText("B") }
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> true }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.move(setOf(source.path), target.path, failingResolver) {} }

        assertEquals(listOf("src"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertTrue("원본이 남아야 함", File(source, "a.txt").exists())
    }

    @Test
    fun `복사에서 폴더 안 한 파일이 입출력 실패로 멈춰도 형제 파일을 복사하고 실패한 파일을 알린다(#504)`() {
        mergeScenarioFailingAtB()
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.copy(setOf(source.path), target.path, mergingFailingResolver) {} }

        assertEquals(listOf("src/b.txt"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertEquals("AAAA", File(target, "src/a.txt").readText())
        assertEquals("C", File(target, "src/c.txt").readText())
    }

    @Test
    fun `다른 파티션 이동에서 폴더 안 한 파일이 실패해도 형제 파일을 옮기고 실패한 파일과 그 폴더만 원본에 남긴다(#504)`() {
        mergeScenarioFailingAtB()
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.move(setOf(source.path), target.path, mergingFailingResolver) {} }

        assertMovedExceptB(result)
    }

    @Test
    fun `같은 파티션 이동에서 폴더 안 한 파일이 실패해도 형제 파일을 옮기고 실패한 파일과 그 폴더만 원본에 남긴다(#504)`() {
        mergeScenarioFailingAtB()
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> true }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.move(setOf(source.path), target.path, mergingFailingResolver) {} }

        assertMovedExceptB(result)
    }

    @Test
    fun `복사에서 실패한 파일의 몫도 진행률에 넘겨 끝나기 전에 모든 파일과 바이트를 센다`() {
        mergeScenarioFailingAtB()
        val reports = mutableListOf<ProgressState>()
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        runBlocking { transfer.copy(setOf(source.path), target.path, mergingFailingResolver) { reports += it } }

        assertCountedAllBeforeComplete(reports)
    }

    @Test
    fun `같은 파티션 이동에서 실패한 파일의 몫도 진행률에 넘겨 끝나기 전에 모든 파일과 바이트를 센다`() {
        mergeScenarioFailingAtB()
        val reports = mutableListOf<ProgressState>()
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> true }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        runBlocking { transfer.move(setOf(source.path), target.path, mergingFailingResolver) { reports += it } }

        assertCountedAllBeforeComplete(reports)
    }

    @Test
    fun `같은 파티션 이동은 원본을 대상 아래로 옮긴다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> true }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.move(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(IoResult.Done, result)
        assertEquals("AAAA", File(target, "src/a.txt").readText())
        assertFalse(source.exists())
    }

    @Test
    fun `다른 파티션 이동은 복사본과 그 이름을 장치에 기록한 뒤에 원본을 지운다(#430)`() {
        // 기록할 때마다 그 순간 원본 파일이 남아 있었는지를 함께 남긴다
        val synced = mutableListOf<Pair<String, Boolean>>()
        val original = File(source, "a.txt")
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = { synced += it.relativeTo(dir).invariantSeparatorsPath to original.exists() })

        val result = runBlocking { transfer.move(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf("dst" to true, "dst/src/a.txt" to true, "dst/src" to true), synced)
        assertFalse(source.exists())
    }

    @Test
    fun `다른 파티션 이동에서 복사본을 장치에 기록하지 못하면 원본을 지우지 않고 실패한다(#430)`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = { if (it.isFile) throw IOException("기록 실패") })

        val result = runBlocking { transfer.move(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(listOf("src/a.txt"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertEquals("AAAA", File(source, "a.txt").readText())
    }

    @Test
    fun `복사는 원본이 남으므로 장치 기록을 기다리지 않는다(#430)`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = { throw AssertionError("복사에서 기록함: $it") })

        val result = runBlocking { transfer.copy(setOf(source.path), target.path, UnexpectedConflictResolver) {} }

        assertEquals(IoResult.Done, result)
    }

    @Test
    fun `여러 항목 복사에서 한 항목이 입출력 실패로 멈춰도 나머지 항목을 복사하고 실패한 항목을 알린다(#502)`() {
        // 대상의 같은 이름 파일이 src 폴더의 겹침을 묻게 하고, 묻는 쪽이 입출력 실패를 던져 첫 항목만 실패시킨다
        File(target, "src").writeText("file")
        val other = File(dir, "b.txt").apply { writeText("B") }
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { Long.MAX_VALUE }, syncToDisk = {})

        val result = runBlocking { transfer.copy(linkedSetOf(source.path, other.path), target.path, failingResolver) {} }

        val failures = (result as IoResult.PartiallyFailed).failures
        assertEquals(listOf("src"), failures.map { it.path })
        assertEquals("B", File(target, "b.txt").readText())
    }

    /**
     * 원본 src에 b.txt·c.txt를 더하고, 대상에 같은 이름의 src 폴더와 그 안의 b.txt를 만든다.
     * [mergingFailingResolver]와 함께 쓰면 폴더는 합치고 b.txt에서만 입출력 실패가 난다.
     */
    private fun mergeScenarioFailingAtB() {
        File(source, "b.txt").writeText("new")
        File(source, "c.txt").writeText("C")
        File(target, "src").apply { mkdirs(); File(this, "b.txt").writeText("old") }
    }

    /** [mergeScenarioFailingAtB]를 이동한 [result]가 b.txt만 실패로 알리고, b.txt와 그 폴더만 원본에 남겼는지 확인한다. */
    private fun assertMovedExceptB(result: IoResult<Unit>) {
        assertEquals(listOf("src/b.txt"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertEquals(listOf("b.txt"), source.list()!!.toList())
        assertEquals("AAAA", File(target, "src/a.txt").readText())
        assertEquals("C", File(target, "src/c.txt").readText())
        assertEquals("old", File(target, "src/b.txt").readText())
    }

    /** [mergeScenarioFailingAtB]의 원본 파일 3개·8바이트를 완료(100%) 보고 전에 모두 센 것을 [reports]로 확인한다. */
    private fun assertCountedAllBeforeComplete(reports: List<ProgressState>) {
        val beforeComplete = reports.dropLast(1).last()
        assertEquals(8L, beforeComplete.writtenBytes)
        assertEquals(3, beforeComplete.currentIndex)
    }

    /** 묻는 순간 입출력 실패를 던지는 resolver. 이동·복사 중간의 입출력 실패를 만든다. */
    private val failingResolver = WriteConflictResolver { throw IOException("만들 수 없음") }

    /** 폴더 겹침은 합치고, 파일 겹침을 묻는 순간 입출력 실패를 던지는 resolver. 폴더 안 한 항목의 입출력 실패를 만든다. */
    private val mergingFailingResolver = WriteConflictResolver {
        if (it.kind == WriteConflictKind.FOLDER) ConflictChoice.PROCEED else throw IOException("만들 수 없음")
    }
}
