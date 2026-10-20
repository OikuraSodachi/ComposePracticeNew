package com.todokanai.composepracticenew.datasource

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [LocalEntryMutator.rename]가 같은 이름의 항목을 덮어쓰기 확인 여부와 종류에 따라 거부·교체·합치기로 처리하는지 검증하는 단위 테스트. */
class LocalEntryMutatorRenameTest {

    private val dir: File = Files.createTempDirectory("rename-test").toFile()
    private val mutator = LocalEntryMutator()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `같은 이름이 없으면 이름을 바꾼다`() = runBlocking {
        val a = file("a.txt", "A")

        mutator.rename(a.path, "b.txt", overwrite = false)

        assertFalse(a.exists())
        assertEquals("A", File(dir, "b.txt").readText())
    }

    @Test
    fun `같은 이름의 파일이 있고 덮어쓰기를 확인하지 않았으면 실패하고 두 파일을 그대로 둔다`() = runBlocking {
        val a = file("a.txt", "A")
        val b = file("b.txt", "B")

        val result = runCatching { mutator.rename(a.path, "b.txt", overwrite = false) }

        assertTrue(result.isFailure)
        assertEquals("A", a.readText())
        assertEquals("B", b.readText())
    }

    @Test
    fun `덮어쓰기를 확인하면 파일은 기존 파일을 교체한다`() = runBlocking {
        val a = file("a.txt", "A")
        file("b.txt", "B")

        mutator.rename(a.path, "b.txt", overwrite = true)

        assertFalse(a.exists())
        assertEquals("A", File(dir, "b.txt").readText())
    }

    @Test
    fun `덮어쓰기를 확인하면 폴더는 합쳐 겹치는 파일만 덮어쓰고 나머지는 유지한다`() = runBlocking {
        file("src/same.txt", "new")
        file("src/only-src.txt", "S")
        file("src/sub/deep.txt", "deep-new")
        File(dir, "src/empty").mkdirs()
        file("dst/same.txt", "old")
        file("dst/only-dst.txt", "D")
        file("dst/sub/keep.txt", "K")

        mutator.rename(File(dir, "src").path, "dst", overwrite = true)

        assertFalse(File(dir, "src").exists())
        assertEquals("new", File(dir, "dst/same.txt").readText())
        assertEquals("S", File(dir, "dst/only-src.txt").readText())
        assertEquals("D", File(dir, "dst/only-dst.txt").readText())
        assertEquals("deep-new", File(dir, "dst/sub/deep.txt").readText())
        assertEquals("K", File(dir, "dst/sub/keep.txt").readText())
        assertTrue(File(dir, "dst/empty").isDirectory)
    }

    @Test
    fun `파일과 폴더처럼 종류가 다르면 덮어쓰기를 확인해도 실패하고 둘 다 그대로 둔다`() = runBlocking {
        val a = file("a.txt", "A")
        file("folder/inner.txt", "I")

        val result = runCatching { mutator.rename(a.path, "folder", overwrite = true) }

        assertTrue(result.isFailure)
        assertEquals("A", a.readText())
        assertEquals("I", File(dir, "folder/inner.txt").readText())
    }

    @Test
    fun `폴더를 합칠 때 하위에 종류가 다른 항목이 있으면 아무것도 바꾸지 않고 실패한다`() = runBlocking {
        file("src/x", "file-in-src")
        file("src/a.txt", "new")
        file("dst/x/inner.txt", "folder-in-dst")
        file("dst/a.txt", "old")

        val result = runCatching { mutator.rename(File(dir, "src").path, "dst", overwrite = true) }

        assertTrue(result.isFailure)
        assertEquals("file-in-src", File(dir, "src/x").readText())
        assertEquals("new", File(dir, "src/a.txt").readText())
        assertEquals("old", File(dir, "dst/a.txt").readText())
        assertEquals("folder-in-dst", File(dir, "dst/x/inner.txt").readText())
    }

    @Test
    fun `대소문자만 바꾸는 이름 변경은 충돌로 보지 않는다`() = runBlocking {
        val a = file("a.txt", "A")

        mutator.rename(a.path, "A.txt", overwrite = false)

        assertEquals(listOf("A.txt"), dir.list()!!.toList())
        assertEquals("A", File(dir, "A.txt").readText())
    }

    /** [dir] 아래 [relative] 경로에 [content] 내용의 파일을 만든다. 상위 폴더도 함께 만든다. */
    private fun file(relative: String, content: String): File =
        File(dir, relative).apply { parentFile!!.mkdirs(); writeText(content) }
}
