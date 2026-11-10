package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.compose.frag.OptionDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [OptionDialogState]의 열기·닫기, 새 폴더 다이얼로그가 연 때의 대상 폴더를 유지하는지, 열 때마다 새 회차 식별자를 발급하는지 검증하는 단위 테스트. */
class OptionDialogStateTest {

    @Test
    fun `dismissAll은 열린 다이얼로그를 모두 닫는다`() {
        val state = OptionDialogState()
        state.openNewFolder("/dir")
        state.openSort()

        state.dismissAll()

        assertNull(state.newFolderTarget)
        assertFalse(state.sortDialog)
    }

    @Test
    fun `새 폴더 다이얼로그는 연 때의 대상 폴더를 닫을 때까지 유지한다`() {
        val state = OptionDialogState()

        state.openNewFolder("/dir")
        assertEquals("/dir", state.newFolderTarget)

        state.dismissNewFolder()
        assertNull(state.newFolderTarget)
    }

    @Test
    fun `다이얼로그를 열 때마다 새 openId를 발급한다`() {
        val state = OptionDialogState()
        state.openNewFolder("/dir")
        val first = state.openId
        state.dismissNewFolder()

        state.openNewFolder("/dir")

        assertEquals("/dir", state.newFolderTarget)
        assertNotEquals(first, state.openId)
    }
}
