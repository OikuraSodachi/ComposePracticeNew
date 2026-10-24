package com.todokanai.composepracticenew.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 진행률 다이얼로그를 한 번에 한 작업에만, 가장 나중에 시작한 작업 순으로 띄우고 스스로 다른 작업으로 넘어가지 않는지 검증하는 단위 테스트. */
class ProgressDialogSelectionTest {

    @Test
    fun `새 작업이 시작되면 그 작업을 띄운다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet()).onActiveIdsChanged(setOf(1))

        assertEquals(1, selection.shownId)
    }

    @Test
    fun `처음 상태를 만들 때 이미 진행 중인 작업은 새 작업으로 보지 않는다`() {
        val selection = ProgressDialogSelection.startingWith(setOf(1, 2)).onActiveIdsChanged(setOf(1, 2))

        assertNull(selection.shownId)
    }

    @Test
    fun `처음 상태 뒤에 시작한 작업은 새 작업으로 띄운다`() {
        val selection = ProgressDialogSelection.startingWith(setOf(1)).onActiveIdsChanged(setOf(1, 2))

        assertEquals(2, selection.shownId)
    }

    @Test
    fun `다른 작업이 진행 중일 때 새 작업이 시작되면 새 작업으로 바꾼다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(1))
            .onActiveIdsChanged(setOf(1, 2))

        assertEquals(2, selection.shownId)
    }

    @Test
    fun `새 작업 여럿이 한꺼번에 보이면 가장 나중에 시작한 작업을 띄운다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet()).onActiveIdsChanged(setOf(3, 1, 2))

        assertEquals(3, selection.shownId)
    }

    @Test
    fun `닫으면 진행 중인 다른 작업을 띄우지 않는다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(1, 2))
            .dismiss(2)
            .onActiveIdsChanged(setOf(1, 2))

        assertNull(selection.shownId)
    }

    @Test
    fun `닫은 뒤 새 작업이 시작되면 새 작업을 띄운다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(1))
            .dismiss(1)
            .onActiveIdsChanged(setOf(1, 2))

        assertEquals(2, selection.shownId)
    }

    @Test
    fun `띄운 작업이 끝나면 진행 중인 다른 작업을 띄우지 않는다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(1, 2))
            .onActiveIdsChanged(setOf(1))

        assertNull(selection.shownId)
    }

    @Test
    fun `이미 다른 작업으로 바뀐 다이얼로그를 닫는 요청은 지금 다이얼로그를 닫지 않는다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(1))
            .onActiveIdsChanged(setOf(1, 2))
            .dismiss(1)

        assertEquals(2, selection.shownId)
    }

    @Test
    fun `요청한 작업을 띄우고 그 뒤 목록이 바뀌어도 유지한다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(1, 2))
            .show(1)
            .onActiveIdsChanged(setOf(1, 2))

        assertEquals(1, selection.shownId)
    }

    @Test
    fun `끝난 작업을 띄우라는 요청은 다음 목록 변경 때 지운다`() {
        val selection = ProgressDialogSelection.startingWith(emptySet())
            .onActiveIdsChanged(setOf(2))
            .show(1)
            .onActiveIdsChanged(setOf(2))

        assertNull(selection.shownId)
    }
}
