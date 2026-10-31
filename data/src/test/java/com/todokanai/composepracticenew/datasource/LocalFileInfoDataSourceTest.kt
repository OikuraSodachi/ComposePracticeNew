package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/** [LocalFileInfoDataSource]가 하위 항목까지 크기를 더하고, 읽지 못한 항목을 하한으로 구분하며, 링크 순환에서도 끝나는지 검증하는 단위 테스트. */
class LocalFileInfoDataSourceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dataSource = LocalFileInfoDataSource(LocalFileDataSource())

    @Test
    fun `중첩 폴더의 파일 크기를 모두 더한다`() = runTest {
        val root = temp.newFolder("root")
        root.resolve("a.bin").writeBytes(ByteArray(10))
        root.resolve("sub/deeper").mkdirs()
        root.resolve("sub/b.bin").writeBytes(ByteArray(20))
        root.resolve("sub/deeper/c.bin").writeBytes(ByteArray(30))

        assertEquals(IoResult.Success(SizeMeasurement.Complete(60)), dataSource.measureSize(listOf(root.absolutePath)))
    }

    @Test
    fun `여러 대상의 크기를 합친다`() = runTest {
        val a = temp.newFile("a.bin").apply { writeBytes(ByteArray(5)) }
        val dir = temp.newFolder("d").apply { resolve("b.bin").writeBytes(ByteArray(7)) }

        assertEquals(IoResult.Success(SizeMeasurement.Complete(12)), dataSource.measureSize(listOf(a.absolutePath, dir.absolutePath)))
    }

    @Test
    fun `읽지 못한 대상은 0으로 치지 않고 하한으로 알린다`() = runTest {
        val present = temp.newFile("a.bin").apply { writeBytes(ByteArray(5)) }
        val missing = temp.root.resolve("missing")

        val measurement = (dataSource.measureSize(listOf(present.absolutePath, missing.absolutePath)) as IoResult.Success).value

        measurement as SizeMeasurement.LowerBound
        assertEquals(5, measurement.bytes)
        assertEquals(listOf(missing.absolutePath), measurement.unreadable.map { it.path })
    }

    @Test
    fun `읽을 수 없는 하위 폴더는 하한으로 알린다`() = runTest {
        val root = temp.newFolder("root")
        root.resolve("a.bin").writeBytes(ByteArray(10))
        val locked = root.resolve("locked").apply { mkdirs(); resolve("hidden.bin").writeBytes(ByteArray(99)) }
        // 권한을 막을 수 없는 환경(Windows, root 계정)에서는 재현할 수 없다
        assumeTrue(locked.setReadable(false) && !locked.canRead())
        try {
            val measurement = (dataSource.measureSize(listOf(root.absolutePath)) as IoResult.Success).value

            measurement as SizeMeasurement.LowerBound
            assertEquals(10, measurement.bytes)
            assertEquals(listOf(locked.absolutePath), measurement.unreadable.map { it.path })
        } finally {
            locked.setReadable(true)
        }
    }

    @Test
    fun `트리 안의 링크는 따라가지 않아 상위를 가리키는 링크가 있어도 끝난다`() = runTest {
        val root = temp.newFolder("root")
        root.resolve("a.bin").writeBytes(ByteArray(10))
        assumeSymlink(root.resolve("loop"), root)

        assertEquals(IoResult.Success(SizeMeasurement.Complete(10)), dataSource.measureSize(listOf(root.absolutePath)))
    }

    @Test
    fun `측정 대상 자신이 링크면 링크가 가리키는 폴더를 잰다`() = runTest {
        val target = temp.newFolder("target").apply { resolve("a.bin").writeBytes(ByteArray(10)) }
        val link = temp.root.resolve("link")
        assumeSymlink(link, target)

        assertEquals(IoResult.Success(SizeMeasurement.Complete(10)), dataSource.measureSize(listOf(link.absolutePath)))
    }

    @Test
    fun `끊긴 링크인 측정 대상은 하한으로 알린다`() = runTest {
        val link = temp.root.resolve("broken")
        assumeSymlink(link, temp.root.resolve("gone"))

        val measurement = (dataSource.measureSize(listOf(link.absolutePath)) as IoResult.Success).value

        assertTrue(measurement is SizeMeasurement.LowerBound)
    }

    @Test
    fun `측정 중 취소되면 다음 방문에서 멈춘다`() {
        val root = temp.newFolder("root").apply { resolve("a.bin").writeBytes(ByteArray(1)) }
        val cancelled = Job().apply { cancel() }

        assertThrows(CancellationException::class.java) {
            Files.walkFileTree(root.toPath(), LocalFileInfoDataSource.SizeVisitor(cancelled))
        }
    }

    @Test
    fun `하위 항목 수는 바로 아래 항목만 센다`() = runTest {
        val root = temp.newFolder("root")
        root.resolve("a.bin").writeText("a")
        root.resolve("sub").mkdirs()
        root.resolve("sub/b.bin").writeText("b")

        assertEquals(IoResult.Success(2), dataSource.countChildren(root.absolutePath))
    }

    @Test
    fun `읽지 못한 폴더의 하위 항목 수는 0이 아니라 실패다`() = runTest {
        assertTrue(dataSource.countChildren(temp.root.resolve("missing").absolutePath) is IoResult.ItemFailed)
    }

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
