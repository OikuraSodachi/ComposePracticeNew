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

    @Test
    fun `기본 모드에서 시작한 드래그는 범위만 선택한다`() {
        assertEquals(setOf("/a", "/b"), pathsOf(DragSelection.Fresh.selectionWith(listOf(a, b))))
    }

    @Test
    fun `선택되지 않은 항목에서 시작하면 범위를 기존 선택에 더한다`() {
        val drag = DragSelection.startingAt(anchor = b, selected = listOf(d))

        assertEquals(setOf("/b", "/c", "/d"), pathsOf(drag.selectionWith(listOf(b, c))))
    }

    @Test
    fun `선택된 항목에서 시작하면 범위를 선택에서 뺀다`() {
        val drag = DragSelection.startingAt(anchor = b, selected = listOf(a, b, c, d))

        assertEquals(setOf("/a", "/d"), pathsOf(drag.selectionWith(listOf(b, c))))
    }

    @Test
    fun `더하는 드래그의 범위가 줄면 빠진 항목은 선택에서 빠진다`() {
        val drag = DragSelection.startingAt(anchor = a, selected = listOf(d))
        drag.selectionWith(listOf(a, b, c))

        assertEquals(setOf("/a", "/d"), pathsOf(drag.selectionWith(listOf(a))))
    }

    @Test
    fun `빼는 드래그의 범위가 줄면 빠진 항목은 다시 선택된다`() {
        val drag = DragSelection.startingAt(anchor = a, selected = listOf(a, b, c))
        drag.selectionWith(listOf(a, b, c))

        assertEquals(setOf("/b", "/c"), pathsOf(drag.selectionWith(listOf(a))))
    }

    @Test
    fun `같은 경로의 다른 인스턴스도 같은 항목으로 본다`() {
        val drag = DragSelection.startingAt(anchor = item("/a"), selected = listOf(a))

        assertEquals(emptySet<String>(), pathsOf(drag.selectionWith(listOf(item("/a")))))
    }

    private fun pathsOf(items: List<FileHolderItem>) = items.mapTo(HashSet()) { it.path }

    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.removePrefix("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
