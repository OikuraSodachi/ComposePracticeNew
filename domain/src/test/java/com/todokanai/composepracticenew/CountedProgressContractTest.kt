package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ProgressPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 항목 수 진행(`ProgressPhase.Counted`)이 계약을 어긴 값을 만들 때 실패하는지 검증한다. */
class CountedProgressContractTest {

    @Test
    fun `전체 수가 0이면 만들 때 실패한다`() {
        assertThrows(IllegalArgumentException::class.java) { ProgressPhase.Counted(0, 0, "a") }
    }

    @Test
    fun `처리 수가 전체 수를 넘으면 만들 때 실패한다`() {
        assertThrows(IllegalArgumentException::class.java) { ProgressPhase.Counted(3, 2, "a") }
    }

    @Test
    fun `처리 수가 음수면 만들 때 실패한다`() {
        assertThrows(IllegalArgumentException::class.java) { ProgressPhase.Counted(-1, 2, "a") }
    }

    @Test
    fun `계약 안의 값은 퍼센트를 낸다`() {
        assertEquals(100, ProgressPhase.Counted(2, 2, "a").percent)
    }
}
