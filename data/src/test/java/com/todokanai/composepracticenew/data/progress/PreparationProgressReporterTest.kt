package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.PreparationProgress
import com.todokanai.composepracticenew.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

/** [PreparationProgressReporter]가 보고 간격 사이에 찾은 항목도 세어, 다음 보고에 지금까지 찾은 수를 담는지 검증하는 단위 테스트. */
class PreparationProgressReporterTest {

    /** 테스트가 정하는 현재 시각(나노초). */
    private var now = 0L
    private val reported = mutableListOf<ProgressState>()
    private val reporter = PreparationProgressReporter({ reported += it }, ProgressThrottle(intervalNanos = 100, now = { now }))

    @Test
    fun `보고 간격 안에 찾은 항목은 보고하지 않고 세기만 해 다음 보고에 담는다`() {
        reporter.onEntryFound("a")
        now = 50
        reporter.onEntryFound("b")
        now = 100
        reporter.onEntryFound("c")

        assertEquals(listOf(PreparationProgress(1, "a"), PreparationProgress(3, "c")), reported.map { it.preparing })
    }
}
