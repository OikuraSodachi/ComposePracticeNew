package com.todokanai.composepracticenew.compose.dialog

import com.todokanai.composepracticenew.compose.listview.BottomButtonDialogState
import com.todokanai.composepracticenew.compose.listview.RemoteBottomButtonDialogState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 화면 단위 다이얼로그 상태가 열 때마다 새 회차를 발급하고, 화면을 떠날 때 모든 다이얼로그를 닫는지 검증하는 단위 테스트. */
class ScreenDialogStateTest {

    @Test
    fun `다시 열면 새 회차(openId)가 발급된다`() {
        val state = BottomButtonDialogState()
        state.entryActions.openRename()
        val first = state.openId
        state.entryActions.dismissRename()

        state.entryActions.openRename()

        assertNotEquals(first, state.openId)
    }

    @Test
    fun `dismissAll은 로컬 하단 버튼의 모든 다이얼로그를 닫는다`() {
        val state = BottomButtonDialogState()
        state.openCompress("/folder")
        state.entryActions.openRename()
        state.entryActions.openDelete()

        state.dismissAll()

        assertNull(state.compressTarget)
        assertFalse(state.entryActions.renameDialog)
        assertFalse(state.entryActions.deleteDialog)
    }

    @Test
    fun `dismissAll은 원격 하단 버튼의 모든 다이얼로그를 닫는다`() {
        val state = RemoteBottomButtonDialogState()
        state.entryActions.openRename()
        state.entryActions.openDelete()

        state.dismissAll()

        assertFalse(state.entryActions.renameDialog)
        assertFalse(state.entryActions.deleteDialog)
    }
}
