package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * 로컬 트리 순회가 안을 나열하지 못한 폴더를 빈 폴더와 구분해 내놓고, 그 순회를 쓰는 작업이 그 폴더를 빈 폴더로 다루지 않는지 검증하는 단위 테스트(#422).
 * 순회 계약은 나열 결과를 바꾼 [File]로 어느 환경에서나 검증하고, 작업별 검증은 나열을 막을 수 없는 환경(root 계정 등)에서 건너뛴다.
 */
class LocalTreeUnlistedFolderTest {

    /** 테스트 트리를 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("local-tree-unlisted-test").toFile()
    /** 작업 대상 폴더. */
    private val source = File(dir, "src").apply { mkdirs(); File(this, "a.txt").writeText("A") }
    /** [source] 안의, 안을 나열하지 못하게 할 폴더. */
    private val locked = File(source, "locked").apply { mkdirs(); File(this, "hidden.txt").writeText("H") }

    @After
    fun tearDown() {
        restoreListable(locked)
        dir.localTreeBottomUp().forEach { it.file.delete() }
    }

    @Test
    fun `순회는 나열하지 못한 폴더를 빈 폴더와 구분해 내놓는다`() {
        val unlistable = object : File(source.path) {
            override fun listFiles(): Array<File>? = null
        }

        val entries = unlistable.localTreeTopDown().map { it.kind }.toList()

        assertEquals(listOf(LocalTreeEntryKind.UNLISTED_DIRECTORY), entries)
    }

    @Test
    fun `압축은 나열하지 못한 폴더를 빈 폴더로 넣지 않고 나머지로 만든 뒤 실패로 알린다`() = runBlocking {
        assumeUnlistable(locked)
        val compression = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to ZipArchiveCompressor()))

        val result = compression.compress(setOf(source.path), dir.path, "out", ArchiveFormat.ZIP, password = null) {}

        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("src/locked", FailureReason.FolderUnreadable()))), result)
        ZipFile(File(dir, "out.zip")).use { zip -> assertEquals(listOf("src/", "src/a.txt"), zip.entries().toList().map { it.name }) }
    }

    @Test
    fun `업로드 전 이름 읽기는 업로드가 만들지 않는 나열하지 못한 폴더를 넣지 않는다`() = runBlocking {
        assumeUnlistable(locked)

        val result = LocalFileInfoDataSource().folderEntryNames(listOf(source.path))

        assertEquals(mapOf("src" to listOf("a.txt")), result.associate { it.folderLabel to it.names })
    }

    @Test
    fun `이름 변경의 합치기는 나열하지 못한 폴더를 같은 이름 폴더에 합쳐야 하면 아무것도 옮기지 않고 실패한다`() = runBlocking {
        val dest = File(dir, "dest").apply { mkdirs(); File(this, "locked").mkdirs() }
        assumeUnlistable(locked)

        val result = LocalEntryMutator().rename(source.path, dest.name, overwrite = true)

        assertEquals(IoResult.ItemFailed(FailureReason.FolderUnreadable("dest/locked")), result)
        assertEquals("A", File(source, "a.txt").readText())
        assertFalse(File(dest, "a.txt").exists())
    }

    @Test
    fun `이름 변경의 합치기는 겹치는 폴더가 없는 나열하지 못한 폴더를 안을 읽지 않고 통째로 옮긴다`() = runBlocking {
        val dest = File(dir, "dest").apply { mkdirs() }
        val moved = File(dest, "locked")
        assumeUnlistable(locked)
        try {
            val result = LocalEntryMutator().rename(source.path, dest.name, overwrite = true)

            assertEquals(IoResult.Done, result)
            assertFalse(source.exists())
            assertEquals("A", File(dest, "a.txt").readText())
        } finally {
            restoreListable(moved)
        }
        assertEquals("H", File(moved, "hidden.txt").readText())
    }
}
