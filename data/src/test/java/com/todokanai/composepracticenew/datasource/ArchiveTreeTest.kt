package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveEntry
import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.ArchiveFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [archiveTreeOf]가 경로 목록을 압축 해제와 같은 위치 규칙으로 폴더 트리로 만들고, 폴더 항목 없는 경로에서 폴더를 만들며,
 * 같은 이름의 폴더는 합치고 파일은 모두 두며, 압축 해제 위치 밖을 가리키는 항목을 숨기지 않고 루트에 두는지 검증하는 단위 테스트.
 */
class ArchiveTreeTest {

    @Test
    fun `폴더 항목이 없으면 파일 경로에서 폴더를 만든다`() {
        val root = archiveTreeOf(listOf(file("a/b/c.txt")))

        assertEquals(listOf("D a", "D a/b", "F a/b/c.txt"), describe(root))
        assertNull((root.entries.single() as ArchiveEntry.Folder).lastModifiedMillis)
    }

    @Test
    fun `폴더 항목은 같은 이름의 만든 폴더와 합치고 그 수정 시각을 쓴다`() {
        val root = archiveTreeOf(listOf(file("a/x.txt"), folder("a/", lastModified = 5L), file("a/y.txt")))

        assertEquals(listOf("D a", "F a/x.txt", "F a/y.txt"), describe(root))
        assertEquals(5L, (root.entries.single() as ArchiveEntry.Folder).lastModifiedMillis)
    }

    @Test
    fun `같은 경로의 파일은 합치지 않고 모두 둔다`() {
        val root = archiveTreeOf(listOf(file("a.txt"), file("a.txt")))

        assertEquals(listOf("F a.txt", "F a.txt"), describe(root))
    }

    @Test
    fun `압축 파일에 담긴 순서를 지킨다`() {
        val root = archiveTreeOf(listOf(file("z.txt"), file("m/n.txt"), file("a.txt")))

        assertEquals(listOf("F z.txt", "D m", "F m/n.txt", "F a.txt"), describe(root))
    }

    @Test
    fun `압축 해제 위치 밖이나 루트 자신을 가리키는 항목은 숨기지 않고 루트에 원래 경로로 둔다`() {
        val root = archiveTreeOf(listOf(file("../evil.txt"), file("/abs.txt"), file("a/../../up.txt"), folder("./"), file("ok.txt")))

        assertEquals(listOf("U ../evil.txt", "U /abs.txt", "U a/../../up.txt", "U ./", "F ok.txt"), describe(root))
    }

    @Test
    fun `안에서 올라가도 루트 안에 머무는 경로는 압축 해제처럼 정리한 위치에 둔다`() {
        val root = archiveTreeOf(listOf(file("a/../b.txt"), file("./c//d.txt")))

        assertEquals(listOf("F b.txt", "D c", "F c/d.txt"), describe(root))
    }

    @Test
    fun `링크와 암호화된 파일은 그 종류로 둔다`() {
        val root = archiveTreeOf(listOf(
            ArchiveEntryInfo("link", ArchiveEntryKind.LINK, size = 10, lastModifiedMillis = null, isEncrypted = false),
            ArchiveEntryInfo("secret.txt", ArchiveEntryKind.FILE, size = 3, lastModifiedMillis = 7L, isEncrypted = true),
        ))

        assertEquals(ArchiveEntry.Link("link", null, ArchiveEntryKey.Item(0)), root.entries[0])
        assertEquals(ArchiveEntry.File("secret.txt", 3, 7L, isEncrypted = true, ArchiveEntryKey.Item(1)), root.entries[1])
    }

    @Test
    fun `폴더가 아닌 항목의 키는 항목 목록에서의 순번이라 이름이 같아도 다르다`() {
        val root = archiveTreeOf(listOf(file("a.txt"), file("../up.txt"), folder("d/"), file("a.txt")))

        assertEquals(
            listOf(ArchiveEntryKey.Item(0), ArchiveEntryKey.Item(1), ArchiveEntryKey.Folder(null, "d"), ArchiveEntryKey.Item(3)),
            root.entries.map { it.key }
        )
    }

    @Test
    fun `폴더의 키는 루트부터의 이름이라 합친 폴더는 키가 하나다`() {
        val root = archiveTreeOf(listOf(file("a/b/x.txt"), folder("a/b/")))

        val b = root.folderAt(listOf("a"))!!.entries.single()
        assertEquals(ArchiveEntryKey.Folder(ArchiveEntryKey.Folder(null, "a"), "b"), b.key)
        assertNotEquals(ArchiveEntryKey.Folder(ArchiveEntryKey.Folder(null, "c"), "b"), b.key)
    }

    @Test
    fun `깊이 중첩된 폴더의 키도 스택이 넘치지 않고 비교한다`() {
        val depth = 100_000
        val deep = (1..depth).fold(null as ArchiveEntryKey.Folder?) { parent, _ -> ArchiveEntryKey.Folder(parent, "d") }
        val same = (1..depth).fold(null as ArchiveEntryKey.Folder?) { parent, _ -> ArchiveEntryKey.Folder(parent, "d") }

        assertEquals(deep, same)
        assertEquals(deep.hashCode(), same.hashCode())
    }

    @Test
    fun `깊이 중첩된 경로도 스택이 넘치지 않고 만든다`() {
        val depth = 100_000
        val root = archiveTreeOf(listOf(file("d/".repeat(depth) + "leaf.txt")))

        val deepest = root.folderAt(List(depth) { "d" })
        assertNotNull(deepest)
        assertEquals(listOf("leaf.txt"), deepest!!.entries.map { it.name })
    }

    @Test
    fun `폴더 이름들로 안쪽 폴더를 찾고 없는 폴더면 null이다`() {
        val root = archiveTreeOf(listOf(file("a/b/c.txt"), file("a.txt")))

        assertEquals(listOf("c.txt"), root.folderAt(listOf("a", "b"))!!.entries.map { it.name })
        assertNull(root.folderAt(listOf("a.txt")))
        assertNull(root.folderAt(listOf("x")))
    }

    /** 크기·시각이 없는 파일 항목. */
    private fun file(path: String) = ArchiveEntryInfo(path, ArchiveEntryKind.FILE, size = null, lastModifiedMillis = null, isEncrypted = false)

    /** 폴더 항목. */
    private fun folder(path: String, lastModified: Long? = null) = ArchiveEntryInfo(path, ArchiveEntryKind.FOLDER, size = null, lastModifiedMillis = lastModified, isEncrypted = false)

    /**
     * 트리를 "종류 경로" 줄로 펼친다(폴더 D, 파일 F, 링크 L, 위험한 경로 U). 폴더는 자기 줄 바로 뒤에 안쪽 항목이 온다.
     * @param folder 펼칠 폴더
     * @param prefix 이 폴더의 경로 접두어
     * @return 담긴 순서의 줄들
     */
    private fun describe(folder: ArchiveFolder, prefix: String = ""): List<String> = folder.entries.flatMap { entry ->
        when (entry) {
            is ArchiveEntry.Folder -> listOf("D $prefix${entry.name}") + describe(entry.contents, "$prefix${entry.name}/")
            is ArchiveEntry.File -> listOf("F $prefix${entry.name}")
            is ArchiveEntry.Link -> listOf("L $prefix${entry.name}")
            is ArchiveEntry.UnsafePath -> listOf("U ${entry.name}")
        }
    }
}
