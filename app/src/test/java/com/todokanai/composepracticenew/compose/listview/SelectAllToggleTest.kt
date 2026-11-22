package com.todokanai.composepracticenew.compose.listview

import androidx.compose.ui.state.ToggleableState
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingKey
import com.todokanai.composepracticenew.ui.model.ListingUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/** 모두 선택 체크박스가 지금 목록의 선택 정도를 나타내고, 누르면 목록 전체 선택과 모두 해제를 오가며, 보이는 목록의 항목만 대상으로 하는지 검증하는 단위 테스트. */
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

    @Test
    fun `모두 선택의 대상은 항목을 보여 주는 목록의 항목이다`() {
        val loaded = ListingUiState.Loaded(items = items, key = ListingKey(path = "/", generation = 1), unreadableCount = 0)

        assertEquals(items, selectAllTargetsOf(loaded))
    }

    @Test
    fun `항목을 보여 주지 않는 목록에서는 모두 선택의 대상이 없다`() {
        val states = listOf(
            ListingUiState.NoPath,
            ListingUiState.Loading,
            ListingUiState.Failed(reason = ListingFailureReason.UNREADABLE, detail = null)
        )

        states.forEach { assertEquals("$it", emptyList<FileHolderItem>(), selectAllTargetsOf(it)) }
    }

    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.removePrefix("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
