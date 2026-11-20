package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/** [LocalEntryMutator.rename]가 같은 이름의 항목을 덮어쓰기 확인 여부와 종류에 따라 거부·교체·합치기로 처리하는지 검증하는 단위 테스트. */
class LocalEntryMutatorRenameTest {

    private val dir: File = Files.createTempDirectory("rename-test").toFile()
    private val mutator = LocalEntryMutator()
    /** 열어 둔 파일을 옮길 수 없는 OS(Windows)에서 실행 중인지 여부. */
    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `같은 이름이 없으면 이름을 바꾼다`() = runBlocking {
        val a = file("a.txt", "A")

        assertEquals(IoResult.Done, mutator.rename(a.path, "b.txt", overwrite = false))

        assertFalse(a.exists())
        assertEquals("A", File(dir, "b.txt").readText())
    }

    @Test
    fun `같은 이름의 파일이 있고 덮어쓰기를 확인하지 않았으면 실패하고 두 파일을 그대로 둔다`() = runBlocking {
        val a = file("a.txt", "A")
        val b = file("b.txt", "B")

        val result = mutator.rename(a.path, "b.txt", overwrite = false)

        assertTrue("$result", result is IoResult.ItemFailed)
        assertEquals("A", a.readText())
        assertEquals("B", b.readText())
    }

    @Test
    fun `덮어쓰기를 확인하면 파일은 기존 파일을 교체한다`() = runBlocking {
        val a = file("a.txt", "A")
        file("b.txt", "B")

        assertEquals(IoResult.Done, mutator.rename(a.path, "b.txt", overwrite = true))

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

        assertEquals(IoResult.Done, mutator.rename(File(dir, "src").path, "dst", overwrite = true))

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

        val result = mutator.rename(a.path, "folder", overwrite = true)

        assertTrue("$result", result is IoResult.ItemFailed)
        assertEquals("A", a.readText())
        assertEquals("I", File(dir, "folder/inner.txt").readText())
    }

    @Test
    fun `폴더를 합칠 때 하위에 종류가 다른 항목이 있으면 아무것도 바꾸지 않고 실패한다`() = runBlocking {
        file("src/x", "file-in-src")
        file("src/a.txt", "new")
        file("dst/x/inner.txt", "folder-in-dst")
        file("dst/a.txt", "old")

        val result = mutator.rename(File(dir, "src").path, "dst", overwrite = true)

        assertTrue("$result", result is IoResult.ItemFailed)
        assertEquals("file-in-src", File(dir, "src/x").readText())
        assertEquals("new", File(dir, "src/a.txt").readText())
        assertEquals("old", File(dir, "dst/a.txt").readText())
        assertEquals("folder-in-dst", File(dir, "dst/x/inner.txt").readText())
    }

    @Test
    fun `폴더를 합치다 옮기지 못한 항목이 있으면 나머지를 끝까지 합치고 옮기지 못한 항목만 원래 폴더 이름부터의 경로로 알린다(#426)`() = runBlocking {
        file("src/a.txt", "A")
        val stuck = file("src/sub/stuck.txt", "S")
        file("src/sub/z.txt", "Z")
        val destSub = File(dir, "dst/sub").apply { mkdirs() }

        val result = blockingMoveOf(stuck, destSub) { mutator.rename(File(dir, "src").path, "dst", overwrite = true) }

        // 쓰기 권한으로 막는 OS에서는 같은 폴더로 가는 z.txt도 막혀 함께 실패한다 — 그래도 남은 항목을 시도했다는 증거다
        val failedPaths = (result as IoResult.PartiallyFailed).failures.map { it.path }
        assertTrue("$failedPaths", "src/sub/stuck.txt" in failedPaths && failedPaths.all { it.startsWith("src/sub/") && it.endsWith(".txt") })
        assertEquals("A", File(dir, "dst/a.txt").readText())
        assertEquals("S", stuck.readText())
        assertFalse(File(dir, "dst/sub/stuck.txt").exists())
        assertTrue(File(dir, "src/sub").isDirectory)
        // 열어 둔 파일만 막히는 Windows에서는 같은 폴더의 다른 항목이 옮겨져야 한다 — 첫 실패에서 멈추지 않았다는 직접 증거
        if (isWindows) assertEquals("Z", File(dir, "dst/sub/z.txt").readText())
    }

    @Test
    fun `합치기에서 겹치는 파일을 교체하지 못하면 덮어쓸 파일을 지우지 않고 그대로 둔다(#432)`() = runBlocking {
        val stuck = file("src/sub/x.txt", "new")
        file("dst/sub/x.txt", "old")

        // 옮길 파일이 든 폴더를 막는다 — 대상을 먼저 지우는 교체라면 대상 쪽 폴더는 쓸 수 있어 대상만 지워진다
        val result = blockingMoveOf(stuck, stuck.parentFile!!) { mutator.rename(File(dir, "src").path, "dst", overwrite = true) }

        assertEquals(listOf("src/sub/x.txt"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertEquals("old", File(dir, "dst/sub/x.txt").readText())
        assertEquals("new", stuck.readText())
    }

    @Test
    fun `대소문자만 바꾸는 이름 변경은 충돌로 보지 않는다`() = runBlocking {
        val a = file("a.txt", "A")

        assertEquals(IoResult.Done, mutator.rename(a.path, "A.txt", overwrite = false))

        assertEquals(listOf("A.txt"), dir.list()!!.toList())
        assertEquals("A", File(dir, "A.txt").readText())
    }

    /**
     * [file]을 옮길 수 없게 막은 채 [block]을 실행한다. Windows는 [file]을 열어 두어(공유 삭제 없이 열린 파일은 옮길 수 없다),
     * 그 밖의 OS는 [lockedDir](옮길 파일이 든 폴더나 받을 폴더)의 쓰기 권한을 빼서 막는다. 막을 수 없는 환경(root 계정 등)에서는 테스트를 건너뛴다.
     */
    private suspend fun <T> blockingMoveOf(file: File, lockedDir: File, block: suspend () -> T): T {
        try {
            if (!isWindows) assumeTrue(lockedDir.setWritable(false) && !Files.isWritable(lockedDir.toPath()))
            return if (isWindows) FileInputStream(file).use { block() } else block()
        } finally {
            lockedDir.setWritable(true)
        }
    }

    /** [dir] 아래 [relative] 경로에 [content] 내용의 파일을 만든다. 상위 폴더도 함께 만든다. */
    private fun file(relative: String, content: String): File =
        File(dir, relative).apply { parentFile!!.mkdirs(); writeText(content) }
}
