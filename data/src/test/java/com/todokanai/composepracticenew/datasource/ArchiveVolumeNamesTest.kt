package com.todokanai.composepracticenew.datasource

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 분할 압축의 이름 규칙(시작 볼륨·다른 볼륨·폴더 이름)과 번호 볼륨 찾기를 검증하는 단위 테스트. */
class ArchiveVolumeNamesTest {

    /** 번호 볼륨을 둘 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("volume-names-test").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `RAR은 part1과 옛 방식 rar가 시작 볼륨이고 나머지 번호와 rNN이 다른 볼륨이다`() {
        assertEntry("a", rarNameRoleOf("a.part1.rar"))
        assertEntry("a", rarNameRoleOf("a.PART001.RAR"))
        assertEntry("a", rarNameRoleOf("a.rar"))
        assertLater("a", rarNameRoleOf("a.part2.rar"))
        assertLater("a", rarNameRoleOf("a.r00"))
        assertLater("a", rarNameRoleOf("a.R12"))
        assertNull(rarNameRoleOf("a.zip"))
    }

    @Test
    fun `RAR의 part 방식과 옛 방식은 같은 이름이어도 다른 세트다`() {
        assertEquals(rarNameRoleOf("a.part1.rar")!!.set, rarNameRoleOf("a.part2.rar")!!.set)
        assertEquals(rarNameRoleOf("a.rar")!!.set, rarNameRoleOf("a.r00")!!.set)
        assertNotSameSet(rarNameRoleOf("a.rar")!!, rarNameRoleOf("a.part2.rar")!!)
    }

    @Test
    fun `ZIP은 zip이 시작 파일이고 zNN이 다른 볼륨이며 번호 분할은 001이 시작 볼륨이다`() {
        assertEntry("a", zipNameRoleOf("a.zip"))
        assertLater("a", zipNameRoleOf("a.z01"))
        assertLater("a", zipNameRoleOf("a.z100"))
        assertEntry("a", zipNameRoleOf("a.zip.001"), VolumeLayout.NUMBERED)
        assertLater("a", zipNameRoleOf("a.zip.002"), VolumeLayout.NUMBERED)
        assertEquals(zipNameRoleOf("a.zip")!!.set, zipNameRoleOf("a.z01")!!.set)
        assertNotSameSet(zipNameRoleOf("a.zip")!!, zipNameRoleOf("a.zip.002")!!)
    }

    @Test
    fun `번호 분할 ZIP은 zip4j가 알아보는 소문자 확장자만 받는다`() {
        assertNull(zipNameRoleOf("a.ZIP.001"))
    }

    @Test
    fun `7z는 7z가 단일 아카이브이고 번호 분할은 대소문자와 무관하게 001이 시작 볼륨이다`() {
        assertEntry("a", sevenZipNameRoleOf("a.7z"))
        assertEntry("a", sevenZipNameRoleOf("a.7Z.001"), VolumeLayout.NUMBERED)
        assertLater("a", sevenZipNameRoleOf("a.7z.010"), VolumeLayout.NUMBERED)
        assertNull(sevenZipNameRoleOf("a.001"))
        assertNull(sevenZipNameRoleOf("a.7z.01"))
    }

    @Test
    fun `볼륨 번호와 확장자를 빼면 이름이 비는 파일은 파일 이름을 폴더 이름으로 쓴다`() {
        assertEntry(".zip", zipNameRoleOf(".zip"))
        assertEntry(".a", zipNameRoleOf(".a.zip"))
    }

    @Test
    fun `번호 볼륨은 이어지는 번호까지 찾는다`() {
        val first = volumes("a.7z.001", "a.7z.002", "a.7z.003")

        assertEquals(listOf("a.7z.001", "a.7z.002", "a.7z.003"), numberedVolumesOf(first).map { it.name })
    }

    @Test
    fun `번호 볼륨의 가운데가 빠지면 빠진 볼륨을 알린다`() {
        val first = volumes("a.7z.001", "a.7z.003")

        val e = assertThrows(MissingArchiveVolumeException::class.java) { numberedVolumesOf(first) }

        assertEquals("a.7z.001" to "a.7z.002", e.archiveName to e.volumeName)
    }

    @Test
    fun `다른 세트의 번호 볼륨은 빠진 볼륨으로 보지 않는다`() {
        val first = volumes("a.7z.001", "b.7z.003", "a.7z.0030")

        assertEquals(listOf("a.7z.001"), numberedVolumesOf(first).map { it.name })
    }

    /**
     * [names]의 빈 파일을 만든다.
     * @return 첫 파일
     */
    private fun volumes(vararg names: String): File = names.map { File(dir, it).apply { writeText("x") } }.first()

    private fun assertEntry(folderName: String, role: ArchiveNameRole?, layout: VolumeLayout = VolumeLayout.SELF_LOCATING) {
        assertEquals(ArchiveNameRole.Entry::class, role!!::class)
        assertEquals(folderName to layout, role.set.folderName to role.set.layout)
    }

    private fun assertLater(folderName: String, role: ArchiveNameRole?, layout: VolumeLayout = VolumeLayout.SELF_LOCATING) {
        assertEquals(ArchiveNameRole.LaterVolume::class, role!!::class)
        assertEquals(folderName to layout, role.set.folderName to role.set.layout)
    }

    private fun assertNotSameSet(a: ArchiveNameRole, b: ArchiveNameRole) {
        assertNotEquals(a.set.id, b.set.id)
    }
}
