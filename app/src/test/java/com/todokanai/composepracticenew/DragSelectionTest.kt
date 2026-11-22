package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.DragSelection
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** 드래그 범위 선택이 시작 때 선택 위에 범위를 더하거나 빼고, 범위가 줄면 빠진 항목을 시작 때 상태로 되돌리는지 검증하는 단위 테스트. */
class DragSelectionTest {

    private val a = item("/a")
    private val b = item("/b")
    private val c = item("/c")
    private val d = item("/d")
    /** 드래그하는 동안 바뀌지 않은 지금 목록. */
    private val listed = listOf(a, b, c, d)

    @Test
    fun `드래그 중 목록에서 사라진 시작 때 선택은 다시 넣지 않는다`() {
        val drag = DragSelection.startingAt(anchor = b, selected = listOf(a, d))

        assertEquals(setOf("/b", "/d"), pathsOf(drag.selectionWith(listOf(b), listed = listOf(b, c, d))))
    }

    @Test
    fun `시작 때 선택의 범위 밖 항목은 지금 목록의 항목으로 바뀐다`() {
        val drag = DragSelection.startingAt(anchor = b, selected = listOf(d))
        val newerD = item("/d", sizeBytes = 7L)

        val selection = drag.selectionWith(listOf(b), listed = listOf(a, b, c, newerD))

        assertEquals(7L, selection.single { it.path == "/d" }.sizeBytes)
    }

    @Test
    fun `기본 모드에서 시작한 드래그는 범위만 선택한다`() {
        assertEquals(setOf("/a", "/b"), pathsOf(DragSelection.Fresh.selectionWith(listOf(a, b), listed)))
    }

    @Test
    fun `선택되지 않은 항목에서 시작하면 범위를 기존 선택에 더한다`() {
        val drag = DragSelection.startingAt(anchor = b, selected = listOf(d))

        assertEquals(setOf("/b", "/c", "/d"), pathsOf(drag.selectionWith(listOf(b, c), listed)))
    }

    @Test
    fun `선택된 항목에서 시작하면 범위를 선택에서 뺀다`() {
        val drag = DragSelection.startingAt(anchor = b, selected = listOf(a, b, c, d))

        assertEquals(setOf("/a", "/d"), pathsOf(drag.selectionWith(listOf(b, c), listed)))
    }

    @Test
    fun `더하는 드래그의 범위가 줄면 빠진 항목은 선택에서 빠진다`() {
        val drag = DragSelection.startingAt(anchor = a, selected = listOf(d))
        drag.selectionWith(listOf(a, b, c), listed)

        assertEquals(setOf("/a", "/d"), pathsOf(drag.selectionWith(listOf(a), listed)))
    }

    @Test
    fun `빼는 드래그의 범위가 줄면 빠진 항목은 다시 선택된다`() {
        val drag = DragSelection.startingAt(anchor = a, selected = listOf(a, b, c))
        drag.selectionWith(listOf(a, b, c), listed)

        assertEquals(setOf("/b", "/c"), pathsOf(drag.selectionWith(listOf(a), listed)))
    }

    @Test
    fun `같은 경로의 다른 인스턴스도 같은 항목으로 본다`() {
        val drag = DragSelection.startingAt(anchor = item("/a"), selected = listOf(a))

        assertEquals(emptySet<String>(), pathsOf(drag.selectionWith(listOf(item("/a")), listed)))
    }

    private fun pathsOf(items: List<FileHolderItem>) = items.mapTo(HashSet()) { it.path }

    private fun item(path: String, sizeBytes: Long? = null) =
        FileHolderItem(path = path, isDirectory = false, name = path.removePrefix("/"), size = "", lastModified = "", sizeBytes = sizeBytes, lastModifiedMillis = 0L)
}
