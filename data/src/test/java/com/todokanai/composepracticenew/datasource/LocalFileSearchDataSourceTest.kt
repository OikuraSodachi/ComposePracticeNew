package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * [LocalFileSearchDataSource]가 여러 루트 아래에서 이름이 맞는 파일·폴더를 찾고(루트 자신은 빼고), 읽지 못한 루트를 알리며,
 * 링크 순환에서도 끝나고, 취소되면 멈추며, 결과를 간격마다 묶어 보내는지 검증하는 단위 테스트.
 */
class LocalFileSearchDataSourceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dataSource = LocalFileSearchDataSource(LocalFileDataSource())

    @Test
    fun `여러 루트의 하위까지 이름이 맞는 파일과 폴더를 찾는다`() = runTest {
        val first = temp.newFolder("first")
        first.resolve("photos/2024").mkdirs()
        first.resolve("photos/2024/Photo1.jpg").writeText("x")
        first.resolve("notes.txt").writeText("x")
        val second = temp.newFolder("second").apply { resolve("old_photo.png").writeText("x") }

        val found = foundPaths(listOf(first, second), "photo")

        assertEquals(
            setOf(first.resolve("photos"), first.resolve("photos/2024/Photo1.jpg"), second.resolve("old_photo.png")).map { it.absolutePath }.toSet(),
            found.toSet()
        )
    }

    @Test
    fun `찾은 폴더는 폴더로, 파일은 크기와 함께 목록 항목으로 만든다`() = runTest {
        val root = temp.newFolder("root")
        root.resolve("match_dir").mkdirs()
        root.resolve("match.bin").writeBytes(ByteArray(4))

        val entries = search(listOf(root), "match").flatMap { it.found }.associateBy { it.name }

        assertTrue(entries.getValue("match_dir").isDirectory)
        assertEquals(4L, entries.getValue("match.bin").size)
    }

    @Test
    fun `루트 자신은 이름이 맞아도 결과에 넣지 않는다`() = runTest {
        val root = temp.newFolder("match_root").apply { resolve("other.txt").writeText("x") }

        assertEquals(emptyList<String>(), foundPaths(listOf(root), "match"))
    }

    @Test
    fun `없는 루트는 읽지 못한 항목으로 알리고 나머지 루트를 계속 훑는다`() = runTest {
        val missing = temp.root.resolve("missing")
        val present = temp.newFolder("present").apply { resolve("match.txt").writeText("x") }

        val batches = search(listOf(missing, present), "match")

        assertEquals(listOf(missing.absolutePath), batches.flatMap { it.unreadable }.map { it.path })
        assertEquals(listOf(present.resolve("match.txt").absolutePath), batches.flatMap { it.found }.map { it.path })
    }

    @Test
    fun `트리 안의 링크는 따라가지 않아 상위를 가리키는 링크가 있어도 끝나고 링크 자신은 이름으로 찾는다`() = runTest {
        val root = temp.newFolder("root").apply { resolve("match.txt").writeText("x") }
        assumeSymlink(root.resolve("match_loop"), root)

        assertEquals(
            setOf(root.resolve("match.txt").absolutePath, root.resolve("match_loop").absolutePath),
            foundPaths(listOf(root), "match").toSet()
        )
    }

    @Test
    fun `트리 안의 폴더 링크는 링크 자신이 아니라 목록과 같이 대상의 종류로 항목을 만든다`() = runTest {
        val root = temp.newFolder("root").apply { resolve("target").mkdirs() }
        assumeSymlink(root.resolve("match_link"), root.resolve("target"))

        val entries = search(listOf(root), "match").flatMap { it.found }.associateBy { it.name }

        assertTrue(entries.getValue("match_link").isDirectory)
    }

    @Test
    fun `훑으며 받은 속성이 있으면 다시 읽지 않고 그 속성으로 항목을 만든다`() {
        val dir = temp.newFolder("dir")
        val file = temp.newFile("file.bin").apply { writeBytes(ByteArray(4)) }
        val dirAttrs = Files.readAttributes(dir.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

        // 파일에 폴더의 속성을 넘기면 다시 읽지 않았음을 종류로 확인할 수 있다
        val entry = LocalFileDataSource().entryOf(file, dirAttrs)

        assertTrue(entry.isDirectory)
        assertEquals(null, entry.size)
    }

    @Test
    fun `취소되면 다음 방문에서 멈춘다`() {
        val root = temp.newFolder("root").apply { resolve("a.txt").writeText("x") }
        val cancelled = Job().apply { cancel() }
        val reporter = LocalFileSearchDataSource.SearchReporter(Long.MAX_VALUE, { 0L }) { }

        assertThrows(CancellationException::class.java) {
            Files.walkFileTree(root.toPath(), LocalFileSearchDataSource.SearchVisitor(root.toPath(), SearchQuery.of("a")!!, cancelled, ::entryOf, reporter))
        }
    }

    @Test
    fun `간격이 지나기 전에는 모아 두고, 지나면 모은 것을 한 묶음으로 보낸다`() {
        var now = 0L
        val reported = mutableListOf<LocalSearchBatch>()
        val reporter = LocalFileSearchDataSource.SearchReporter(intervalNanos = 10, nanoTime = { now }) { reported += it }
        val a = entry("a")
        val b = entry("b")
        val failed = FailedItem("/x", null)

        reporter.found(a)
        reporter.unreadable(failed)
        now = 9
        reporter.reportIfDue()
        assertEquals(emptyList<LocalSearchBatch>(), reported)

        reporter.found(b)
        now = 10
        reporter.reportIfDue()
        assertEquals(listOf(LocalSearchBatch(listOf(a, b), listOf(failed))), reported)
    }

    @Test
    fun `마지막에 남은 것은 간격과 무관하게 보내고, 모은 것이 없으면 보내지 않는다`() {
        val reported = mutableListOf<LocalSearchBatch>()
        val reporter = LocalFileSearchDataSource.SearchReporter(Long.MAX_VALUE, { 0L }) { reported += it }
        val a = entry("a")

        reporter.flush()
        reporter.found(a)
        reporter.flush()

        assertEquals(listOf(LocalSearchBatch(listOf(a), emptyList())), reported)
    }

    /** [roots] 아래에서 [text]를 찾은 결과 묶음을 모두 모은다. */
    private suspend fun search(roots: List<File>, text: String): List<LocalSearchBatch> =
        dataSource.search(roots.map { it.absolutePath }, SearchQuery.of(text)!!).toList()

    /** [roots] 아래에서 [text]를 찾은 항목의 경로를 모두 모은다. */
    private suspend fun foundPaths(roots: List<File>, text: String): List<String> =
        search(roots, text).flatMap { it.found }.map { it.path }

    /** 방문자가 찾은 경로의 목록 항목을 만드는 함수 — 취소 검증에서는 항목 내용을 보지 않는다. */
    @Suppress("UNUSED_PARAMETER")
    private fun entryOf(path: java.nio.file.Path, attrs: BasicFileAttributes?): FileEntry = entry(path.toString())

    /** 이름이 [name]인 파일 항목. */
    private fun entry(name: String): FileEntry = FileEntry(name = name, path = "/$name", isDirectory = false, size = null, lastModified = 0L)

    /** [link]에 [target]을 가리키는 심볼릭 링크를 만든다. 링크를 만들 수 없는 환경(권한 없는 Windows 등)이면 테스트를 건너뛴다. */
    private fun assumeSymlink(link: File, target: File) {
        val created = try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        } catch (e: IOException) {
            false
        } catch (e: UnsupportedOperationException) {
            false
        }
        assumeTrue(created)
    }
}
