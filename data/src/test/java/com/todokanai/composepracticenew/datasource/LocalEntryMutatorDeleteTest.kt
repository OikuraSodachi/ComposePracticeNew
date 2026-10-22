package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/** [LocalEntryMutator.delete]가 트리를 모두 지우고, 지우지 못한 항목이 있으면 나머지를 끝까지 지운 뒤 실패를 알리는지 검증하는 단위 테스트. */
class LocalEntryMutatorDeleteTest {

    private val dir: File = Files.createTempDirectory("delete-test").toFile()
    private val mutator = LocalEntryMutator()

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    @Test
    fun `디렉터리 트리를 모두 지우고 100퍼센트까지 보고한다`() = runBlocking {
        val target = File(dir, "target").apply { mkdirs() }
        File(target, "sub").mkdirs()
        File(target, "a.txt").writeText("A")
        File(target, "sub/b.txt").writeText("B")
        val reports = mutableListOf<ProgressState>()

        mutator.delete(target.path) { reports += it }

        assertFalse(target.exists())
        assertEquals(100, reports.last().progress)
    }

    @Test
    fun `이미 없는 경로는 삭제된 것으로 보고 실패하지 않는다`() = runBlocking {
        mutator.delete(File(dir, "missing").path) {}
    }

    @Test
    fun `지우지 못한 항목이 있어도 나머지를 지우고 실패한 항목을 알린다`() {
        // 지울 수 없는 파일을 만든다 — Windows는 열린 파일을, POSIX는 쓰기 금지 디렉터리 안의 파일을 지우지 못한다
        val target = File(dir, "target").apply { mkdirs() }
        val lockedDir = File(target, "locked").apply { mkdirs() }
        val locked = File(lockedDir, "locked.txt").apply { writeText("L") }
        val free = File(target, "free.txt").apply { writeText("F") }

        val result = FileInputStream(locked).use {
            lockedDir.setWritable(false)
            try {
                runCatching { runBlocking { mutator.delete(target.path) {} } }
            } finally {
                lockedDir.setWritable(true)
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.exceptionOrNull()?.message.orEmpty().contains("locked/locked.txt"))
        assertFalse("지울 수 있는 항목은 지워야 함", free.exists())
        assertTrue(locked.exists())
    }
}
