package com.todokanai.composepracticenew.compose.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LazyList 콘텐츠 오른쪽 끝에 세로 스크롤바 thumb를 overlay로 그리는 Modifier.
 *
 * @param state 연결할 [LazyListState]
 * @param thumbColor thumb 색상
 * @param thumbWidth thumb 너비
 * @param minThumbHeightFraction 전체 높이 대비 thumb 최소 비율
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    thumbColor: Color = Color.Gray.copy(alpha = 0.6f),
    thumbWidth: Dp = 4.dp,
    minThumbHeightFraction: Float = 0.05f
): Modifier = composed {
    val thumbWidthPx = with(LocalDensity.current) { thumbWidth.toPx() }

    drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo

        if (totalItems <= 0 || visibleItems.isEmpty()) return@drawWithContent

        val thumbHeightFraction = (visibleItems.size.toFloat() / totalItems)
            .coerceIn(minThumbHeightFraction, 1f)

        if (thumbHeightFraction >= 1f) return@drawWithContent

        val itemHeight = visibleItems.first().size.toFloat()
        if (itemHeight <= 0f) return@drawWithContent

        // firstVisibleItemScrollOffset를 아이템 단위로 정규화하여 연속적인 스크롤 진행도 계산
        val scrollProgress = ((state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemHeight)
                / (totalItems - visibleItems.size).coerceAtLeast(1))
            .coerceIn(0f, 1f)

        val thumbHeight = size.height * thumbHeightFraction
        val thumbOffset = (size.height - thumbHeight) * scrollProgress

        drawRect(
            color = thumbColor,
            topLeft = Offset(x = size.width - thumbWidthPx, y = thumbOffset),
            size = Size(width = thumbWidthPx, height = thumbHeight)
        )
    }
}
