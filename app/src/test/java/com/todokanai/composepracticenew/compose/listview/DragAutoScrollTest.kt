package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 드래그 선택의 자동 스크롤 속도가 최대 속도를 넘지 않고, 손가락이 행 위에 없어도 범위의 끝점이 가장 가까운 행으로 정해지며, 손을 뗀 뒤 가려진 끝 행을 올릴 높이를 구하는지 검증하는 단위 테스트(#521). */
class DragAutoScrollTest {

    private val viewport = 1000f
    private val edge = 80f
    private val max = 15f

    @Test
    fun `가장자리 영역 밖에서는 스크롤하지 않는다`() {
        assertEquals(0f, edgeScrollSpeed(500f, viewport, edge, max))
        assertEquals(0f, edgeScrollSpeed(edge, viewport, edge, max))
        assertEquals(0f, edgeScrollSpeed(viewport - edge, viewport, edge, max))
    }

    @Test
    fun `가장자리 영역 안에서는 끝에 가까울수록 빨라진다`() {
        assertEquals(-max / 2, edgeScrollSpeed(edge / 2, viewport, edge, max), 0.001f)
        assertEquals(max / 2, edgeScrollSpeed(viewport - edge / 2, viewport, edge, max), 0.001f)
        assertEquals(-max, edgeScrollSpeed(0f, viewport, edge, max), 0.001f)
        assertEquals(max, edgeScrollSpeed(viewport, viewport, edge, max), 0.001f)
    }

    @Test
    fun `목록 밖에서는 멀어져도 최대 속도를 넘지 않는다`() {
        assertEquals(-max, edgeScrollSpeed(-160f, viewport, edge, max), 0.001f)
        assertEquals(max, edgeScrollSpeed(viewport + 160f, viewport, edge, max), 0.001f)
    }

    private val rows = listOf(row("/a", offset = 0), row("/b", offset = 100), row("/c", offset = 200))

    @Test
    fun `행 위에서는 그 행을 고른다`() {
        assertEquals("/a", dragTargetKeyIn(rows, 0, 0f))
        assertEquals("/b", dragTargetKeyIn(rows, 0, 150f))
        assertEquals("/c", dragTargetKeyIn(rows, 0, 299f))
    }

    @Test
    fun `목록 위로 나가면 첫 행을 고른다`() {
        assertEquals("/a", dragTargetKeyIn(rows, 0, -50f))
    }

    @Test
    fun `마지막 행 아래 빈 곳이나 목록 아래로 나가면 마지막 행을 고른다`() {
        assertEquals("/c", dragTargetKeyIn(rows, 0, 350f))
        assertEquals("/c", dragTargetKeyIn(rows, 0, 5000f))
    }

    @Test
    fun `위쪽 안쪽 여백만큼 배치 좌표를 보정한다`() {
        val padded = listOf(row("/a", offset = -20), row("/b", offset = 80))

        assertEquals("/a", dragTargetKeyIn(padded, -20, 50f))
        assertEquals("/b", dragTargetKeyIn(padded, -20, 100f))
    }

    @Test
    fun `배치된 행이 없으면 끝점이 없다`() {
        assertNull(dragTargetKeyIn(emptyList(), 0, 10f))
    }

    @Test
    fun `아래쪽 여백에 가려진 높이만큼 올려야 한다`() {
        // 목록 끝 1000, 여백(버튼) 150 → 보이는 끝 850
        assertEquals(50, hiddenBelowVisibleEnd(row("/a", offset = 800), viewportEndOffset = 1000, afterContentPadding = 150))
        assertEquals(100, hiddenBelowVisibleEnd(row("/a", offset = 850), viewportEndOffset = 1000, afterContentPadding = 150))
    }

    @Test
    fun `보이는 영역 안의 행은 올리지 않는다`() {
        assertEquals(0, hiddenBelowVisibleEnd(row("/a", offset = 750), viewportEndOffset = 1000, afterContentPadding = 150))
        assertEquals(0, hiddenBelowVisibleEnd(row("/a", offset = 100), viewportEndOffset = 1000, afterContentPadding = 0))
    }

    private fun row(key: String, offset: Int, size: Int = 100) = object : LazyListItemInfo {
        override val index = 0
        override val key: Any = key
        override val offset = offset
        override val size = size
    }
}
