package com.todokanai.composepracticenew.datasource

import net.sf.sevenzipjbinding.ISeekableStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** [ConcatenatedInStream]이 여러 파일을 이어 붙인 하나의 스트림처럼 읽고 옮기는지 검증하는 단위 테스트. */
class ConcatenatedInStreamTest {

    /** 볼륨 파일을 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("concatenated-stream-test").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `볼륨 경계를 넘어 끝까지 읽으면 이어 붙인 내용이 된다`() {
        ConcatenatedInStream(files("abc", "", "de", "fghij")).use { stream ->
            assertArrayEquals("abcdefghij".toByteArray(), readAll(stream))
        }
    }

    @Test
    fun `한 번의 읽기는 볼륨 경계를 넘지 않는다`() {
        ConcatenatedInStream(files("abc", "de")).use { stream ->
            val buffer = ByteArray(10)
            assertEquals(3, stream.read(buffer))
            assertEquals(2, stream.read(buffer))
            assertEquals(0, stream.read(buffer))
        }
    }

    @Test
    fun `기준마다 옮긴 위치에서 읽는다`() {
        ConcatenatedInStream(files("abc", "de", "fgh")).use { stream ->
            assertEquals(4L, stream.seek(4, ISeekableStream.SEEK_SET))
            assertEquals("e", readString(stream, 1))
            assertEquals(6L, stream.seek(1, ISeekableStream.SEEK_CUR))
            assertEquals("gh", readString(stream, 5))
            assertEquals(6L, stream.seek(-2, ISeekableStream.SEEK_END))
            assertEquals("gh", readString(stream, 5))
        }
    }

    /**
     * [contents]를 내용으로 하는 파일들을 순서대로 만든다.
     * @return 만든 파일들
     */
    private fun files(vararg contents: String): List<File> =
        contents.mapIndexed { i, content -> File(dir, "a.7z.%03d".format(i + 1)).apply { writeText(content) } }

    /** [stream]을 끝까지 읽는다. */
    private fun readAll(stream: ConcatenatedInStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(2)
        while (true) {
            val read = stream.read(buffer)
            if (read == 0) return out.toByteArray()
            out.write(buffer, 0, read)
        }
    }

    /** [stream]에서 한 번 읽은 내용(최대 [size]바이트)을 문자열로 돌려준다. */
    private fun readString(stream: ConcatenatedInStream, size: Int): String {
        val buffer = ByteArray(size)
        return String(buffer, 0, stream.read(buffer))
    }
}
