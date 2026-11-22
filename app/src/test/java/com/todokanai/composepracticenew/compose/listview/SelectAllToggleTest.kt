package com.todokanai.composepracticenew.compose.listview

import androidx.compose.ui.state.ToggleableState
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** 모두 선택 체크박스가 지금 목록의 선택 정도를 나타내고, 누르면 목록 전체 선택과 모두 해제를 오가는지 검증하는 단위 테스트. */
class SelectAllToggleTest {

    private val a = item("/a")
    private val b = item("/b")
    private val items = listOf(a, b)

    @Test
    fun `선택 정도에 따라 체크박스 상태가 정해진다`() {
        assertEquals(ToggleableState.Off, selectAllStateOf(items, emptyList()))
        assertEquals(ToggleableState.Indeterminate, selectAllStateOf(items, listOf(a)))
        assertEquals(ToggleableState.On, selectAllStateOf(items, listOf(b, a)))
    }

    @Test
    fun `목록 밖 항목만 선택돼 있으면 선택하지 않은 것으로 본다`() {
        assertEquals(ToggleableState.Off, selectAllStateOf(items, listOf(item("/elsewhere"))))
    }

    @Test
    fun `빈 목록은 선택하지 않은 것으로 본다`() {
        assertEquals(ToggleableState.Off, selectAllStateOf(emptyList(), emptyList()))
    }

    @Test
    fun `모두 선택돼 있지 않으면 목록 전체를 선택한다`() {
        assertEquals(items, selectionAfterSelectAllToggle(items, emptyList()))
        assertEquals(items, selectionAfterSelectAllToggle(items, listOf(a)))
    }

    @Test
    fun `모두 선택돼 있으면 모두 해제한다`() {
        assertEquals(emptyList<FileHolderItem>(), selectionAfterSelectAllToggle(items, listOf(a, b)))
    }

    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.removePrefix("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
