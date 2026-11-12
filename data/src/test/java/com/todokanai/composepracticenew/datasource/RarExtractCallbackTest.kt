package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * [RarExtractCallback]이 폴더 항목을 만들고(#317), 쓰기 실패·풀 수 없는 항목에서 건너뛰지 않고 멈추며, 처음 던진 예외를 보관하는지 검증하는 단위 테스트(#235).
 * RAR 라이브러리는 JVM에서 열 수 없으므로, 라이브러리가 부르는 순서(getStream → 기록 → setOperationResult)대로 콜백을 직접 부른다.
 */
class RarExtractCallbackTest {

    /** 추출 루트를 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("rar-extract-callback-test").toFile()
    /** 추출 루트. */
    private val root = File(dir, "out").also { it.mkdirs() }
    /** 항목을 루트 아래에 만드는 쓰기 도우미. */
    private val writer = ArchiveEntryWriter(ExtractionRoot(root), UnexpectedConflictAsk, tracker())

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `폴더 항목은 빈 디렉터리로 만든다`() {
        val callback = callbackOf(RarEntryKind.Folder("empty"))

        assertNull(callback.getStream(0, ExtractAskMode.EXTRACT))
        callback.setOperationResult(ExtractOperationResult.OK)

        assertTrue(File(root, "empty").isDirectory)
        assertNull(callback.failure)
    }

    @Test
    fun `파일 항목은 기록한 내용으로 만든다`() {
        val callback = callbackOf(RarEntryKind.File("d/a.txt"))

        callback.getStream(0, ExtractAskMode.EXTRACT)!!.write("hi".toByteArray())
        callback.setOperationResult(ExtractOperationResult.OK)

        assertEquals("hi", File(root, "d/a.txt").readText())
        assertNull(callback.failure)
    }

    @Test
    fun `OK가 아닌 결과는 항목 경로를 담은 IOException으로 멈추고 이후 콜백도 같은 예외로 멈춘다`() {
        val callback = callbackOf(RarEntryKind.File("a.txt"), RarEntryKind.File("b.txt"))

        callback.getStream(0, ExtractAskMode.EXTRACT)!!.write("x".toByteArray())
        val thrown = assertThrows(IOException::class.java) { callback.setOperationResult(ExtractOperationResult.CRCERROR) }

        assertTrue(thrown.message!!.contains("a.txt"))
        assertSame(thrown, callback.failure)
        assertSame(thrown, assertThrows(IOException::class.java) { callback.getStream(1, ExtractAskMode.EXTRACT) })
        assertTrue(!File(root, "b.txt").exists())
    }

    @Test
    fun `파일을 만들지 못하면 건너뛰지 않고 그 IOException을 보관한다`() {
        File(root, "p").writeText("file")
        // 폴더 자리의 파일로 겹침을 묻게 하고, 묻는 쪽이 입출력 실패를 던지게 해 writer가 IOException을 내는 상황을 만든다
        val failingWriter = ArchiveEntryWriter(ExtractionRoot(root), { throw IOException("만들 수 없음") }, tracker())
        val callback = RarExtractCallback({ RarEntryKind.File("p/a.txt") }, { null }, failingWriter, tracker())

        val thrown = assertThrows(IOException::class.java) { callback.getStream(0, ExtractAskMode.EXTRACT) }

        assertSame(thrown, callback.failure)
        assertEquals(0, failingWriter.skippedEntries)
    }

    @Test
    fun `구현 오류는 바꾸지 않고 그대로 보관한다`() {
        val bug = IllegalStateException("bug")
        val callback = RarExtractCallback({ throw bug }, { null }, writer, tracker())

        assertThrows(IllegalStateException::class.java) { callback.getStream(0, ExtractAskMode.EXTRACT) }

        assertSame(bug, callback.failure)
    }

    @Test
    fun `루트 밖을 가리키는 항목은 쓰지 않고 건너뜀으로만 센다`() {
        val callback = callbackOf(RarEntryKind.File("../evil.txt"))

        assertNull(callback.getStream(0, ExtractAskMode.EXTRACT))
        callback.setOperationResult(ExtractOperationResult.OK)

        assertEquals(1, writer.skippedEntries)
        assertNull(callback.failure)
    }

    /** [entries]를 순서대로 항목 번호에 둔 콜백을 만든다. 크기는 모두 모름(null)으로 둔다. */
    private fun callbackOf(vararg entries: RarEntryKind) = RarExtractCallback({ entries[it] }, { null }, writer, tracker())

    /** 진행 보고를 버리는 트래커. */
    private fun tracker() = ByteProgressTracker(1, 1, onProgress = {})
}
