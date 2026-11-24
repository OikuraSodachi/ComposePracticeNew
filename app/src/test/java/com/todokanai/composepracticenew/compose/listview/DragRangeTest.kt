package com.todokanai.composepracticenew.compose.listview

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** 드래그 범위가 두 끝점을 위치가 아니라 키로 지금 목록에서 찾아, 드래그 중 목록이 바뀌어도 시작한 항목부터 잡히는지 검증하는 단위 테스트. */
class DragRangeTest {

    private val a = item("/a")
    private val b = item("/b")
    private val c = item("/c")
    private val d = item("/d")

    @Test
    fun `시작한 항목과 손가락 아래 항목 사이를 방향과 관계없이 잡는다`() {
        val list = listOf(a, b, c, d)

        assertEquals(DragRange.Found(listOf(b, c, d)), dragRangeIn(list, ::fileListKeyOf, "/b", "/d"))
        assertEquals(DragRange.Found(listOf(b, c, d)), dragRangeIn(list, ::fileListKeyOf, "/d", "/b"))
        assertEquals(DragRange.Found(listOf(c)), dragRangeIn(list, ::fileListKeyOf, "/c", "/c"))
    }

    @Test
    fun `시작한 항목 위의 항목이 빠져도 시작한 항목부터 잡는다`() {
        val afterDelete = listOf(b, c, d)

        assertEquals(DragRange.Found(listOf(c, d)), dragRangeIn(afterDelete, ::fileListKeyOf, "/c", "/d"))
    }

    @Test
    fun `목록이 시작할 때 위치보다 짧아져도 크래시 없이 지금 목록에서 잡는다`() {
        val shrunk = listOf(c, d)

        assertEquals(DragRange.Found(listOf(c, d)), dragRangeIn(shrunk, ::fileListKeyOf, "/d", "/c"))
    }

    @Test
    fun `두 끝 사이에 끼어든 항목은 범위에 들어간다`() {
        val inserted = item("/bb")
        val afterInsert = listOf(a, b, inserted, c)

        assertEquals(DragRange.Found(listOf(b, inserted, c)), dragRangeIn(afterInsert, ::fileListKeyOf, "/b", "/c"))
    }

    @Test
    fun `시작한 항목이 목록에서 사라지면 범위를 정하지 않는다`() {
        assertEquals(DragRange.AnchorGone, dragRangeIn(listOf(a, c), ::fileListKeyOf, "/b", "/c"))
    }

    @Test
    fun `손가락 아래 항목이 지금 목록에 없으면 이번 보고만 건너뛴다`() {
        assertEquals(DragRange.FingerUnresolved, dragRangeIn(listOf(a, b), ::fileListKeyOf, "/a", "/gone"))
    }

    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.removePrefix("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
