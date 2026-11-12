package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.ZipFile

/**
 * 로컬 작업이 심볼릭 링크를 따라가지 않는지 검증하는 단위 테스트(#320).
 * 삭제는 링크 자신만 지우고, 항목을 하나씩 다루는 작업(복사·이동·이름 변경의 합치기·압축·업로드 전 이름 읽기)은 링크를 건너뛰어 실패로 알린다.
 * 링크를 만들 수 없는 환경에서는 건너뛴다.
 */
class LocalTreeSymlinkTest {

    /** 테스트 트리를 둘 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("local-tree-symlink-test").toFile()
    /** 링크가 가리킬, 작업 대상 밖의 폴더. */
    private val outside = File(dir, "outside").apply { mkdirs(); File(this, "keep.txt").writeText("K") }
    /** 작업 대상 폴더. */
    private val source = File(dir, "src").apply { mkdirs(); File(this, "a.txt").writeText("A") }
    /** 복사·이동 목적지 폴더. */
    private val target = File(dir, "dst").apply { mkdirs() }

    @After
    fun tearDown() {
        // 링크를 따라가지 않고 지운다 — 순환 링크가 있어도 끝나고, 링크 대상을 두 번 지우지 않도록
        dir.localTreeBottomUp().forEach { it.file.delete() }
    }

    @Test
    fun `순회는 링크를 항목으로 내놓되 안으로 들어가지 않는다`() {
        assumeSymlink(File(source, "loop"), source)

        val entries = source.localTreeTopDown().associate { source.slashRelativePathOf(it.file) to it.kind }

        assertEquals(mapOf("" to LocalEntryKind.DIRECTORY, "a.txt" to LocalEntryKind.FILE, "loop" to LocalEntryKind.LINK), entries)
    }

    @Test
    fun `삭제는 트리 안의 링크 자신만 지우고 링크가 가리키는 폴더는 남긴다`() = runBlocking {
        assumeSymlink(File(source, "link"), outside)

        assertEquals(IoResult.Done, LocalEntryMutator().delete(source.path) {})

        assertFalse(source.exists())
        assertEquals("K", File(outside, "keep.txt").readText())
    }

    @Test
    fun `삭제 대상 자신이 링크면 링크만 지운다`() = runBlocking {
        val link = File(dir, "link")
        assumeSymlink(link, outside)

        assertEquals(IoResult.Done, LocalEntryMutator().delete(link.path) {})

        assertFalse(Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS))
        assertEquals("K", File(outside, "keep.txt").readText())
    }

    @Test
    fun `복사는 순환 링크를 따라가지 않고 끝나며 링크를 건너뛴 실패로 알린다`() = runBlocking {
        assumeSymlink(File(source, "loop"), source)

        val result = transfer(samePartition = false).copy(setOf(source.path), target.path, UnexpectedConflictResolver) {}

        assertEquals(IoResult.PartiallyFailed("복사하지 못함", listOf(FailedItem("src/loop", UNSUPPORTED_LINK_REASON))), result)
        assertEquals(listOf("a.txt"), File(target, "src").list()!!.toList())
    }

    @Test
    fun `다른 파티션으로 이동하면 링크는 원래 자리에 남고 나머지만 옮긴다`() = runBlocking {
        assumeSymlink(File(source, "link"), outside)

        val result = transfer(samePartition = false).move(setOf(source.path), target.path, UnexpectedConflictResolver) {}

        assertEquals(IoResult.PartiallyFailed("이동하지 못함", listOf(FailedItem("src/link", UNSUPPORTED_LINK_REASON))), result)
        assertEquals("A", File(target, "src/a.txt").readText())
        assertTrue(Files.isSymbolicLink(File(source, "link").toPath()))
        assertEquals("K", File(outside, "keep.txt").readText())
    }

    @Test
    fun `같은 파티션에서 합치며 이동할 때 링크가 가리키는 폴더 안을 꺼내 옮기지 않는다`() = runBlocking {
        File(target, "src").mkdirs()
        assumeSymlink(File(source, "link"), outside)
        // 같은 이름의 폴더가 있으면 합치기로 내려가 안쪽 항목을 하나씩 다룬다
        val result = transfer(samePartition = true).move(setOf(source.path), target.path, ProceedingConflictResolver) {}

        assertEquals(IoResult.PartiallyFailed("이동하지 못함", listOf(FailedItem("src/link", UNSUPPORTED_LINK_REASON))), result)
        assertEquals("A", File(target, "src/a.txt").readText())
        assertEquals("K", File(outside, "keep.txt").readText())
    }

    @Test
    fun `선택한 항목 자신이 링크면 이동하지 않고 실패로 알린다`() = runBlocking {
        val link = File(dir, "link")
        assumeSymlink(link, outside)

        val result = transfer(samePartition = true).move(setOf(link.path), target.path, UnexpectedConflictResolver) {}

        assertEquals(IoResult.PartiallyFailed("이동하지 못함", listOf(FailedItem("link", UNSUPPORTED_LINK_REASON))), result)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertFalse(File(target, "link").exists())
    }

    @Test
    fun `이름 변경의 합치기는 링크가 있으면 아무것도 옮기지 않고 실패한다`() = runBlocking {
        val dest = File(dir, "dest").apply { mkdirs() }
        assumeSymlink(File(source, "link"), outside)

        val result = LocalEntryMutator().rename(source.path, dest.name, overwrite = true)

        assertEquals(IoResult.ItemFailed("이름 변경 실패: $UNSUPPORTED_LINK_REASON (dest/link)"), result)
        assertEquals("A", File(source, "a.txt").readText())
        assertEquals("K", File(outside, "keep.txt").readText())
    }

    @Test
    fun `링크의 이름을 그 링크가 가리키는 파일 이름으로 바꾸면 파일을 링크로 대신하지 않고 실패한다`() = runBlocking {
        val file = File(dir, "a.txt").apply { writeText("A") }
        val link = File(dir, "b")
        assumeSymlink(link, file.absoluteFile)

        val result = LocalEntryMutator().rename(link.path, file.name, overwrite = false)

        assertTrue("$result", result is IoResult.ItemFailed)
        assertFalse(Files.isSymbolicLink(file.toPath()))
        assertEquals("A", file.readText())
    }

    @Test
    fun `링크를 같은 이름의 파일 위에 덮어쓰는 이름 변경은 파일을 바꾸지 않고 실패한다`() = runBlocking {
        val file = File(dir, "c.txt").apply { writeText("C") }
        val link = File(dir, "link")
        assumeSymlink(link, File(outside, "keep.txt").absoluteFile)

        val result = LocalEntryMutator().rename(link.path, file.name, overwrite = true)

        assertEquals(IoResult.ItemFailed("이름 변경 실패: $UNSUPPORTED_LINK_REASON (c.txt)"), result)
        assertEquals("C", file.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
    }

    @Test
    fun `파일을 같은 이름의 링크 위에 덮어쓰는 이름 변경은 링크와 그 대상을 바꾸지 않고 실패한다`() = runBlocking {
        val file = File(dir, "c.txt").apply { writeText("C") }
        val link = File(dir, "link")
        assumeSymlink(link, File(outside, "keep.txt").absoluteFile)

        val result = LocalEntryMutator().rename(file.path, link.name, overwrite = true)

        assertEquals(IoResult.ItemFailed("이름 변경 실패: $UNSUPPORTED_LINK_REASON (link)"), result)
        assertEquals("C", file.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("K", File(outside, "keep.txt").readText())
    }

    @Test
    fun `지우지 못한 끊긴 링크는 없는 항목으로 보지 않고 실패로 알린다`() = runBlocking {
        val link = File(dir, "dangling")
        assumeSymlink(link, File(dir, "missing"))

        val result = LocalEntryMutator { false }.delete(link.path) {}

        assertEquals(IoResult.PartiallyFailed("삭제하지 못함", listOf(FailedItem("", null))), result)
    }

    @Test
    fun `압축은 링크를 넣지 않고 나머지로 만든 뒤 넣지 못한 링크를 알린다`() = runBlocking {
        assumeSymlink(File(source, "loop"), source)
        val compression = LocalArchiveCompression(mapOf(ArchiveFormat.ZIP to ZipArchiveCompressor()))

        val result = compression.compress(setOf(source.path), dir.path, "out", ArchiveFormat.ZIP) {}

        assertEquals(IoResult.PartiallyFailed("압축에 넣지 못함", listOf(FailedItem("src/loop", UNSUPPORTED_LINK_REASON))), result)
        ZipFile(File(dir, "out.zip")).use { zip -> assertEquals(listOf("src/", "src/a.txt"), zip.entries().toList().map { it.name }) }
    }

    @Test
    fun `업로드 전 이름 읽기는 업로드가 올리지 않는 링크를 넣지 않는다`() = runBlocking {
        assumeSymlink(File(source, "loop"), source)

        val result = LocalFileInfoDataSource().folderEntryNames(listOf(source.path))

        assertEquals(mapOf("src" to listOf("a.txt")), result.associate { it.folderLabel to it.names })
    }

    /** 같은 이름의 항목을 만나면 언제나 진행(폴더는 합치기)을 고르는 충돌 판정자. */
    private val ProceedingConflictResolver = WriteConflictResolver { ConflictChoice.PROCEED }

    /**
     * 파티션 판별을 정하고 여유 공간은 넉넉한 transfer를 만든다.
     * @param samePartition 원본과 목적지를 같은 파티션으로 볼지 여부
     */
    private fun transfer(samePartition: Boolean) = LocalTreeTransfer(isSamePartition = { _, _ -> samePartition }, freeSpaceOf = { Long.MAX_VALUE })
}
