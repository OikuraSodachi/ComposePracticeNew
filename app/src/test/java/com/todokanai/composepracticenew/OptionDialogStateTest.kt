package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.compose.frag.OptionDialogState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [OptionDialogState]의 열기·닫기와, 열 때마다 새 회차 식별자를 발급하는지 검증하는 단위 테스트. */
class OptionDialogStateTest {

    @Test
    fun `dismissAll은 열린 다이얼로그를 모두 닫는다`() {
        val state = OptionDialogState()
        state.openNewFolder()
        state.openSort()

        state.dismissAll()

        assertFalse(state.newFolderDialog)
        assertFalse(state.sortDialog)
    }

    @Test
    fun `다이얼로그를 열 때마다 새 openId를 발급한다`() {
        val state = OptionDialogState()
        state.openNewFolder()
        val first = state.openId
        state.dismissNewFolder()

        state.openNewFolder()

        assertTrue(state.newFolderDialog)
        assertNotEquals(first, state.openId)
    }
}
