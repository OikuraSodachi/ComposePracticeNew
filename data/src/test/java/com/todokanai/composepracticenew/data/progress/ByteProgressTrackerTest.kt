package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ByteProgressTracker]가 전체 크기를 알면 퍼센트로, 모르면 퍼센트를 지어내지 않고 누적 바이트만 시간 간격마다 보고하는지 검증하는 단위 테스트. */
class ByteProgressTrackerTest {

    private val reports = mutableListOf<ProgressState>()
    /** 테스트가 움직이는 가짜 시각(나노초). */
    private var now = 0L

    @Test
    fun `전체 크기를 알면 누적 바이트의 퍼센트를 보고한다`() {
        val tracker = ByteProgressTracker(totalBytes = 200, totalFileCount = 1, onProgress = { reports += it })

        tracker.startFile()
        tracker.addBytes(50, "a", 200)

        assertEquals(25, reports.single().progress)
        assertEquals(200L, reports.single().totalBytes)
    }

    @Test
    fun `전체 크기를 모르면 진행률과 전체 크기 없이 누적 바이트와 파일 순번을 보고한다`() {
        val tracker = unknownTotalTracker(totalFileCount = 2)

        tracker.startFile()
        tracker.addBytes(1_000, "a", null)

        val state = reports.single()
        assertNull(state.progress)
        assertNull(state.totalBytes)
        assertEquals(1_000L, state.writtenBytes)
        assertEquals(1, state.currentIndex)
        assertEquals(2, state.listSize)
    }

    @Test
    fun `전체 크기를 모르면 바이트가 아무리 늘어도 시간 간격마다 계속 보고한다`() {
        val tracker = unknownTotalTracker(totalFileCount = 1)
        tracker.startFile()

        tracker.addBytes(10, "a", null)
        tracker.addBytes(10, "a", null)
        now += INTERVAL
        tracker.addBytes(1_000_000, "a", null)

        assertEquals(listOf(10L, 1_000_020L), reports.map { it.writtenBytes })
        assertEquals(listOf(null, null), reports.map { it.progress })
    }

    @Test
    fun `전체 크기를 몰라도 마지막 파일을 마치면 받은 바이트를 전체로 100%를 보고한다`() {
        val tracker = unknownTotalTracker(totalFileCount = 1)
        tracker.startFile()
        tracker.addBytes(30, "a", null)

        tracker.onFileCompleted("a", null)

        val last = reports.last()
        assertEquals(100, last.progress)
        assertEquals(30L, last.totalBytes)
        assertEquals(30L, last.writtenBytes)
    }

    private fun unknownTotalTracker(totalFileCount: Int) = ByteProgressTracker(
        totalBytes = null,
        totalFileCount = totalFileCount,
        onProgress = { reports += it },
        holdBelow100 = true,
        throttle = ProgressThrottle(intervalNanos = INTERVAL, now = { now })
    )

    private companion object {
        const val INTERVAL = 1_000L
    }
}
