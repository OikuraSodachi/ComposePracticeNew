package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * [LocalEntryMutator.delete]가 트리를 모두 지우고, 지우지 못한 항목이 있으면 나머지를 끝까지 지운 뒤 실패를 알리는지 검증하는 단위 테스트.
 * 삭제 실패는 실제 파일 잠금·권한이 아니라 주입한 삭제 함수로 재현해 OS·실행 권한(root 등)과 무관하게 같은 결과를 낸다.
 */
class LocalEntryMutatorDeleteTest {

    private val dir: File = Files.createTempDirectory("delete-test").toFile()
    private val mutator = LocalEntryMutator()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `디렉터리 트리를 모두 지우고 100퍼센트까지 보고한다`() = runBlocking {
        val target = File(dir, "target").apply { mkdirs() }
        File(target, "sub").mkdirs()
        File(target, "a.txt").writeText("A")
        File(target, "sub/b.txt").writeText("B")
        val reports = mutableListOf<ProgressState>()

        assertEquals(IoResult.Done, mutator.delete(target.path) { reports += it })

        assertFalse(target.exists())
        assertEquals(100, reports.last().progress)
    }

    @Test
    fun `이미 없는 경로는 삭제된 것으로 보고 실패하지 않는다`() = runBlocking {
        assertEquals(IoResult.Done, mutator.delete(File(dir, "missing").path) {})
    }

    @Test
    fun `지우지 못한 항목이 있어도 나머지를 지우고 원인 항목만 알린다`() {
        val target = File(dir, "target").apply { mkdirs() }
        val locked = File(target, "locked").apply { mkdirs() }.let { File(it, "locked.txt").apply { writeText("L") } }
        val free = File(target, "free.txt").apply { writeText("F") }
        // OS·권한과 무관하게 locked.txt만 지우지 못하게 한다
        val mutator = LocalEntryMutator { file -> file != locked && file.delete() }

        val result = runBlocking { mutator.delete(target.path) {} }

        assertEquals("삭제하지 못함: locked/locked.txt", (result as IoResult.PartiallyFailed).message)
        assertFalse("지울 수 있는 항목은 지워야 함", free.exists())
        assertTrue(locked.exists())
    }

    @Test
    fun `첫 실패 뒤에도 끝까지 진행해 서로 다른 폴더의 실패를 모두 모은다`() {
        val target = File(dir, "target").apply { mkdirs() }
        val lockedA = File(target, "a").apply { mkdirs() }.let { File(it, "x.txt").apply { writeText("X") } }
        val lockedB = File(target, "b").apply { mkdirs() }.let { File(it, "y.txt").apply { writeText("Y") } }
        val mutator = LocalEntryMutator { file -> file != lockedA && file != lockedB && file.delete() }

        val result = runBlocking { mutator.delete(target.path) {} }

        // 순회 순서는 파일시스템마다 다르므로 실패 경로를 집합으로 비교한다 — 첫 실패에서 멈추면 하나만 남는다
        assertEquals(setOf("a/x.txt", "b/y.txt"), (result as IoResult.PartiallyFailed).failures.map { it.path }.toSet())
    }

    @Test
    fun `지우지 못한 항목을 품은 상위 폴더는 삭제를 시도하지 않는다`() {
        val target = File(dir, "target").apply { mkdirs() }
        val sub = File(target, "sub").apply { mkdirs() }
        val locked = File(sub, "locked.txt").apply { writeText("L") }
        val attempts = mutableListOf<File>()
        val mutator = LocalEntryMutator { file -> attempts += file; file != locked && file.delete() }

        runBlocking { mutator.delete(target.path) {} }

        assertEquals(listOf(locked), attempts)
    }
}
