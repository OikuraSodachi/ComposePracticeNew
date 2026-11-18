package com.todokanai.composepracticenew.datasource

import net.sf.sevenzipjbinding.ISeekableStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** [SevenZipInput.SelfLocating]이 라이브러리의 볼륨 요청을 기록하고 다시 열 때 그 기록과 읽기 위치를 처음으로 돌리는지, [closeAll]이 모두 닫는지 검증하는 단위 테스트. */
class SevenZipInputTest {

    /** 볼륨 파일을 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("seven-zip-input-test").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `요청한 볼륨이 있으면 지금 볼륨이 되고 없으면 빠진 볼륨으로 남는다`() {
        val first = volumes("a.part1.rar", "a.part2.rar")

        SevenZipInput.SelfLocating(first).use { input ->
            input.stream()
            input.open("a.part2.rar")
            assertNull(input.open("a.part3.rar"))

            assertEquals(File(dir, "a.part2.rar").path to "a.part3.rar", input.currentVolumeName to input.missingVolume)
        }
    }

    @Test
    fun `다시 열면 지금 볼륨은 시작 파일이 되고 빠진 볼륨 기록은 지워진다`() {
        val first = volumes("a.part1.rar", "a.part2.rar")

        SevenZipInput.SelfLocating(first).use { input ->
            input.stream()
            input.open("a.part2.rar")
            input.open("a.part3.rar")

            input.stream()

            assertEquals(first.path to null, input.currentVolumeName to input.missingVolume)
        }
    }

    @Test
    fun `이전 열기가 읽다 만 볼륨도 다시 주면 처음부터 읽는다`() {
        val first = volumes("a.part1.rar", "a.part2.rar")

        SevenZipInput.SelfLocating(first).use { input ->
            input.stream().read(ByteArray(1))
            input.open("a.part2.rar")!!.read(ByteArray(1))

            assertEquals(0L to 0L, input.stream().seek(0, ISeekableStream.SEEK_CUR) to input.open("a.part2.rar")!!.seek(0, ISeekableStream.SEEK_CUR))
        }
    }

    @Test
    fun `하나를 닫지 못해도 나머지를 모두 닫고 첫 실패에 뒤의 실패를 붙여 던진다`() {
        val closed = mutableListOf<Int>()
        val first = IOException("1")
        val third = IOException("3")
        val closeables = listOf(
            Closeable { closed += 1; throw first },
            Closeable { closed += 2 },
            Closeable { closed += 3; throw third },
        )

        val e = assertThrows(IOException::class.java) { closeAll(closeables) }

        assertEquals(listOf(1, 2, 3), closed)
        assertSame(first, e)
        assertEquals(listOf(third), e.suppressed.toList())
    }

    /**
     * [names]의 파일을 만든다.
     * @return 첫 파일
     */
    private fun volumes(vararg names: String): File = names.map { File(dir, it).apply { writeText("x") } }.first()
}
