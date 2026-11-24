package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveEntryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [selectedEntryPathsOf]가 내용 보기 트리([archiveTreeOf])의 키 규칙대로 고른 항목을 찾아 고른 폴더 기준의 경로를 정하는지,
 * [listingIdOf]가 목록이 다르면 다른 값을 내는지 검증하는 단위 테스트.
 */
class ExtractionScopeTest {

    @Test
    fun `루트에서 고른 파일은 그 이름으로, 고른 폴더는 폴더 항목과 안쪽 항목 전체를 루트 기준 경로로 푼다`() {
        val entries = listOf(file("a.txt"), folder("d/"), file("d/x.txt"), file("d/sub/y.txt"), file("b.txt"))

        val paths = selectedEntryPathsOf(entries, emptyList(), setOf(ArchiveEntryKey.Item(0), ArchiveEntryKey.Folder("d")))

        assertEquals(mapOf(0 to "a.txt", 1 to "d", 2 to "d/x.txt", 3 to "d/sub/y.txt"), paths)
    }

    @Test
    fun `안쪽 폴더에서 고른 항목은 그 폴더 기준 경로로 풀고 폴더 밖 항목은 고르지 않는다`() {
        val entries = listOf(file("x.txt"), file("d/x.txt"), file("d/y.txt"), file("d/sub/z.txt"), file("e/sub/w.txt"))

        val paths = selectedEntryPathsOf(entries, listOf("d"), setOf(ArchiveEntryKey.Item(1), ArchiveEntryKey.Folder("sub")))

        assertEquals(mapOf(1 to "x.txt", 3 to "sub/z.txt"), paths)
    }

    @Test
    fun `폴더 키는 이름이 같은 파일을 고르지 않고 순번 키는 같은 이름의 다른 파일을 고르지 않는다`() {
        val entries = listOf(file("a"), file("a/inner.txt"), file("dup.txt"), file("dup.txt"))

        val paths = selectedEntryPathsOf(entries, emptyList(), setOf(ArchiveEntryKey.Folder("a"), ArchiveEntryKey.Item(3)))

        assertEquals(mapOf(1 to "a/inner.txt", 3 to "dup.txt"), paths)
    }

    @Test
    fun `경로를 정리해 같은 위치가 되는 항목은 정리한 위치로 고른다`() {
        val entries = listOf(file("./d//x.txt"), file("q/../d/y.txt"))

        val paths = selectedEntryPathsOf(entries, listOf("d"), setOf(ArchiveEntryKey.Item(0), ArchiveEntryKey.Item(1)))

        assertEquals(mapOf(0 to "x.txt", 1 to "y.txt"), paths)
    }

    @Test
    fun `루트에서 고른 풀 수 없는 경로는 원래 경로 그대로 넘기고 안쪽 폴더에서는 고르지 않는다`() {
        val entries = listOf(file("../evil.txt"))

        assertEquals(mapOf(0 to "../evil.txt"), selectedEntryPathsOf(entries, emptyList(), setOf(ArchiveEntryKey.Item(0))))
        assertEquals(emptyMap<Int, String>(), selectedEntryPathsOf(entries, listOf("d"), setOf(ArchiveEntryKey.Item(0))))
    }

    @Test
    fun `목록 식별값은 같은 목록이면 같고 항목 하나라도 다르면 다르다`() {
        val entries = listOf(file("a.txt"), folder("d/"))

        assertEquals(listingIdOf(entries), listingIdOf(entries.toList()))
        assertNotEquals(listingIdOf(entries), listingIdOf(listOf(file("a.txt", size = 2), folder("d/"))))
        assertNotEquals(listingIdOf(entries), listingIdOf(entries.reversed()))
        assertNotEquals(listingIdOf(listOf(file("ab"), file("c"))), listingIdOf(listOf(file("a"), file("bc"))))
    }

    /** [path] 파일 항목의 목록 정보. */
    private fun file(path: String, size: Long = 1) = ArchiveEntryInfo(path, ArchiveEntryKind.FILE, size, lastModifiedMillis = null, isEncrypted = false)

    /** [path] 폴더 항목의 목록 정보. */
    private fun folder(path: String) = ArchiveEntryInfo(path, ArchiveEntryKind.FOLDER, size = null, lastModifiedMillis = null, isEncrypted = false)
}
