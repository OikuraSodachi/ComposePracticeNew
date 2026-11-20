package com.todokanai.composepracticenew.datasource

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 기기에서 [forceToDisk]가 폴더를 열어 기록하는지, 그 기록을 쓰는 교차 파티션 이동이 끝까지 성공하는지 검증하는 계측 테스트(#430).
 * 단위 테스트가 도는 Windows는 폴더를 열 수 없어, 앱이 쓰는 폴더 기록 경로는 기기에서만 실행된다.
 */
@RunWith(AndroidJUnit4::class)
class LocalDiskSyncDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    /** 내부 저장소(앱 전용 폴더)의 테스트 폴더. */
    private val internalDir = File(context.filesDir, "disk-sync-test").apply { mkdirs() }
    /** 외부 저장소(앱 전용 폴더)의 테스트 폴더. */
    private val externalDir = File(context.getExternalFilesDir(null)!!, "disk-sync-test").apply { mkdirs() }

    @After
    fun tearDown() {
        internalDir.deleteRecursively()
        externalDir.deleteRecursively()
    }

    @Test
    fun 내부_저장소의_폴더를_기록할_수_있다() {
        File(internalDir, "a.txt").writeText("A")

        forceToDisk(internalDir)

        assertEquals(listOf("a.txt"), internalDir.list()!!.toList())
    }

    @Test
    fun 외부_저장소의_폴더를_기록할_수_있다() {
        File(externalDir, "a.txt").writeText("A")

        forceToDisk(externalDir)

        assertEquals(listOf("a.txt"), externalDir.list()!!.toList())
    }

    @Test
    fun 실제_기록을_쓰는_교차_파티션_이동이_원본을_옮긴다() {
        val source = File(internalDir, "src").apply { mkdirs(); File(this, "sub").mkdirs(); File(this, "sub/a.txt").writeText("A") }
        // 파티션 판별만 바꿔 복사 후 삭제 경로를 타게 한다 — 기록은 앱과 같은 forceToDisk를 쓴다
        val transfer = LocalTreeTransfer(isSamePartition = { _, _ -> false }, freeSpaceOf = File::getFreeSpace, syncToDisk = ::forceToDisk)

        val result = runBlocking { transfer.move(setOf(source.path), externalDir.path, { error("겹침 없음: $it") }) {} }

        assertEquals(IoResult.Done, result)
        assertEquals("A", File(externalDir, "src/sub/a.txt").readText())
        assertFalse(source.exists())
    }
}
