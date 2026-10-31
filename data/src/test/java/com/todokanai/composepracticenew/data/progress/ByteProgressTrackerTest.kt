package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [ByteProgressTracker]가 전체 크기를 알면 퍼센트로, 모르면 퍼센트를 지어내지 않고 누적 바이트와 크기를 아는 부분의 진행을
 * 시간 간격마다 보고하는지 검증하는 단위 테스트.
 */
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
        assertNull(last.knownSize)
    }

    @Test
    fun `전체 크기를 모르면 크기를 아는 파일과 모르는 파일의 몫을 나눠 보고한다`() {
        val tracker = unknownTotalTracker(totalFileCount = 2, knownBytes = 100, unknownFileCount = 1)

        tracker.startFile()
        tracker.addBytes(100, "known", 100)
        tracker.onFileCompleted("known", 100)
        now += INTERVAL
        tracker.startFile()
        tracker.addBytes(500, "unknown", null)

        assertEquals(KnownSizeProgress(100, 100, 0, 1, 0), reports.first().knownSize)
        assertEquals(KnownSizeProgress(100, 100, 100, 1, 500), reports.last().knownSize)
        assertEquals(600L, reports.last().writtenBytes)
    }

    @Test
    fun `크기를 아는 파일의 확정은 보고 간격을 기다리지 않고 바로 보고한다`() {
        val tracker = unknownTotalTracker(totalFileCount = 2, knownBytes = 4, unknownFileCount = 1)

        tracker.startFile()
        tracker.addBytes(4, "known", 4)
        tracker.onFileCompleted("known", 4)

        assertEquals(KnownSizeProgress(4, 4, 4, 1, 0), reports.last().knownSize)
        assertEquals(2, reports.size)
    }

    @Test
    fun `전체 크기가 0이면 진행 중 퍼센트를 내지 않고 완료 때 0바이트로 100%를 보고한다`() {
        val tracker = ByteProgressTracker(totalBytes = 0, totalFileCount = 1, onProgress = { reports += it })

        tracker.startFile()
        tracker.addBytes(5, "grown", 0)
        tracker.reportComplete()

        assertEquals(listOf(100), reports.map { it.progress })
        assertEquals(0L, reports.single().totalBytes)
    }

    @Test
    fun `clampTo100이면 크기 정보보다 많이 처리해도 100을 넘겨 보고하지 않는다`() {
        val tracker = ByteProgressTracker(totalBytes = 10, totalFileCount = 1, onProgress = { reports += it }, clampTo100 = true)

        tracker.startFile()
        tracker.addBytes(30, "entry", 10)

        assertEquals(100, reports.single().progress)
    }

    @Test
    fun `모순된 전체 크기는 만들 때 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) { TransferTotal.Known(-1) }
        assertThrows(IllegalArgumentException::class.java) { TransferTotal.Partial(knownBytes = 10, unknownFileCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { TransferTotal.Partial(knownBytes = -1, unknownFileCount = 1) }
    }

    @Test
    fun `완료 응답을 받지 못한 파일의 몫은 확정하지 않고 다음 파일에서 버린다`() {
        val tracker = unknownTotalTracker(totalFileCount = 3, knownBytes = 8, unknownFileCount = 1)

        tracker.startFile()
        tracker.addBytes(4, "failed", 4)
        now += INTERVAL
        tracker.startFile()
        tracker.addBytes(2, "next", 4)

        assertEquals(KnownSizeProgress(8, 4, 0, 1, 0), reports[0].knownSize)
        assertEquals(KnownSizeProgress(8, 2, 0, 1, 0), reports[1].knownSize)
    }

    @Test
    fun `완료된 파일은 받은 바이트가 알려진 크기보다 적어도 알려진 크기 전부로 센다`() {
        val tracker = unknownTotalTracker(totalFileCount = 2, knownBytes = 100, unknownFileCount = 1)

        tracker.startFile()
        tracker.addBytes(40, "shrunk", 100)
        tracker.onFileCompleted("shrunk", 100)
        now += INTERVAL
        tracker.startFile()
        tracker.addBytes(10, "unknown", null)

        assertEquals(KnownSizeProgress(100, 100, 100, 1, 10), reports.last().knownSize)
    }

    @Test
    fun `목록 조회 뒤 커진 파일은 아는 부분에 알려진 크기까지만 센다`() {
        val tracker = unknownTotalTracker(totalFileCount = 2, knownBytes = 100, unknownFileCount = 1)

        tracker.startFile()
        tracker.addBytes(80, "grown", 100)
        tracker.addBytes(80, "grown", 100)
        now += INTERVAL
        tracker.addBytes(80, "grown", 100)

        assertEquals(100L, reports.last().knownSize?.knownWrittenBytes)
        assertEquals(0L, reports.last().knownSize?.unknownWrittenBytes)
        assertEquals(240L, reports.last().writtenBytes)
    }

    private fun unknownTotalTracker(totalFileCount: Int, knownBytes: Long = 0, unknownFileCount: Int = totalFileCount) = ByteProgressTracker(
        total = TransferTotal.Partial(knownBytes, unknownFileCount),
        totalFileCount = totalFileCount,
        onProgress = { reports += it },
        holdBelow100 = true,
        throttle = ProgressThrottle(intervalNanos = INTERVAL, now = { now })
    )

    private companion object {
        const val INTERVAL = 1_000L
    }
}
