package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * [LocalTreeTransfer]가 여유 공간 부족과 이동 실패를 결과로 알리고, 실패하면 아무것도 쓰지 않거나 원본을 남기는지 검증하는 단위 테스트.
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
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { 1L })

        val result = runBlocking { transfer.copy(setOf(source.path), target.path) {} }

        assertTrue("$result", (result as IoResult.ItemFailed).message.orEmpty().startsWith("디스크 공간 부족: 필요"))
        assertFalse(File(target, "src").exists())
    }

    @Test
    fun `다른 파티션으로 옮길 때 대상에 여유 공간이 없으면 원본을 그대로 두고 실패한다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = { 0L })

        val result = runBlocking { transfer.move(setOf(source.path), target.path) {} }

        assertEquals(IoResult.ItemFailed("디스크 공간 부족: 여유 공간 없음"), result)
        assertTrue(File(source, "a.txt").exists())
        assertFalse(File(target, "src").exists())
    }

    @Test
    fun `같은 파티션에서 비어 있지 않은 같은 이름 폴더로는 옮기지 못하고 어느 항목인지와 함께 실패를 알린다`() {
        File(target, "src").apply { mkdirs(); File(this, "b.txt").writeText("B") }
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> true }, freeSpaceOf = { Long.MAX_VALUE })

        val result = runBlocking { transfer.move(setOf(source.path), target.path) {} }

        val message = (result as IoResult.ItemFailed).message.orEmpty()
        assertTrue(message, message.startsWith("이동 실패: src — "))
        assertTrue(message, message.endsWith("(0개 항목은 이미 이동됨)"))
        assertTrue("원본이 남아야 함", File(source, "a.txt").exists())
    }

    @Test
    fun `같은 파티션 이동은 원본을 대상 아래로 옮긴다`() {
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> true }, freeSpaceOf = { Long.MAX_VALUE })

        val result = runBlocking { transfer.move(setOf(source.path), target.path) {} }

        assertEquals(IoResult.Done, result)
        assertEquals("AAAA", File(target, "src/a.txt").readText())
        assertFalse(source.exists())
    }
}
