package com.todokanai.composepracticenew.datasource

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [forceToDisk]가 파일과 폴더를 열어 기록하고 내용을 바꾸지 않는지 검증하는 단위 테스트. */
class LocalDiskSyncTest {

    private val dir: File = Files.createTempDirectory("disk-sync-test").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `파일을 기록해도 내용은 그대로다`() {
        val file = File(dir, "a.txt").apply { writeText("A") }

        forceToDisk(file)

        assertEquals("A", file.readText())
    }

    @Test
    fun `폴더를 기록할 수 있다`() {
        // Windows는 폴더를 채널로 열 수 없다 — 앱이 도는 Android에서는 계측 테스트 LocalDiskSyncDeviceTest가 확인한다
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        File(dir, "a.txt").writeText("A")

        forceToDisk(dir)

        assertEquals(listOf("a.txt"), dir.list()!!.toList())
    }
}
