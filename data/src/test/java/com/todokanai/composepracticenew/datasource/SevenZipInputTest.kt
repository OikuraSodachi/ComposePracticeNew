package com.todokanai.composepracticenew.datasource

import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.SevenZipException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

/** [SevenZipInput.SelfLocating]이 라이브러리의 볼륨 요청을 기록하고 다시 열 때 그 기록과 읽기 위치를 처음으로 돌리는지, 볼륨을 열지 못한 실패를 보관하는지, [closeAll]이 모두 닫는지 검증하는 단위 테스트. */
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
    fun `요청한 볼륨을 열지 못하면 그 실패를 읽기 실패로 보관한다`() {
        val first = volumes("a.part1.rar", "a.part2.rar")
        val locked = File(dir, "a.part2.rar")
        assumeUnreadable(locked)

        try {
            SevenZipInput.SelfLocating(first).use { input ->
                input.stream()

                assertThrows(SevenZipException::class.java) { input.open("a.part2.rar") }
                assertTrue(input.readFailure is IOException)
            }
        } finally {
            restoreReadable(locked)
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

    /**
     * [file]을 열 수 없게 한다. POSIX 권한으로 막지 못하면(Windows) 소유자의 읽기를 거부하는 ACL 항목을 더하고, 어느 쪽으로도 막지 못하면 테스트를 건너뛴다.
     * @param file 열 수 없게 할 파일
     */
    private fun assumeUnreadable(file: File) {
        if (!(file.setReadable(false) && !canOpen(file))) aclViewOf(file)?.let { view -> view.acl = listOf(readDenialOf(view)) + view.acl }
        assumeTrue(!canOpen(file))
    }

    /**
     * [assumeUnreadable]이 막은 [file]의 읽기를 다시 허용한다.
     * @param file 되돌릴 파일
     */
    private fun restoreReadable(file: File) {
        file.setReadable(true)
        val view = aclViewOf(file) ?: return
        view.acl = view.acl.filterNot { it.type() == AclEntryType.DENY && AclEntryPermission.READ_DATA in it.permissions() }
    }

    /** [file]을 읽기로 열 수 있는지. */
    private fun canOpen(file: File): Boolean = try {
        RandomAccessFile(file, "r").close()
        true
    } catch (e: IOException) {
        false
    }

    /** [file]의 ACL 보기. ACL이 없는 파일 시스템이면 null. */
    private fun aclViewOf(file: File): AclFileAttributeView? = Files.getFileAttributeView(file.toPath(), AclFileAttributeView::class.java)

    /** [view]의 소유자에게 파일 읽기를 거부하는 ACL 항목. */
    private fun readDenialOf(view: AclFileAttributeView): AclEntry = AclEntry.newBuilder()
        .setType(AclEntryType.DENY)
        .setPrincipal(view.owner)
        .setPermissions(AclEntryPermission.READ_DATA)
        .build()
}
