package com.todokanai.composepracticenew.compose.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LazyList 콘텐츠 오른쪽 끝에 세로 스크롤바 thumb를 overlay로 그리는 Modifier.
 *
 * @param state 연결할 [LazyListState]
 * @param thumbColor thumb 색상
 * @param thumbWidth thumb 너비
 * @param minThumbHeightFraction 전체 높이 대비 thumb 최소 비율
 * @return 스크롤바 thumb가 오른쪽에 overlay로 그려진 Modifier
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    thumbColor: Color = Color.Gray.copy(alpha = 0.6f),
    thumbWidth: Dp = 4.dp,
    minThumbHeightFraction: Float = 0.05f
): Modifier = drawWithContent {
    drawContent()
    // DrawScope가 Density를 구현하므로 composed/LocalDensity 불필요
    val thumbWidthPx = thumbWidth.toPx()

    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo

    if (totalItems <= 0 || visibleItems.isEmpty()) return@drawWithContent

    val thumbHeightFraction = (visibleItems.size.toFloat() / totalItems)
        .coerceIn(minThumbHeightFraction, 1f)

    if (thumbHeightFraction >= 1f) return@drawWithContent

    val itemHeight = visibleItems.first().size.toFloat()
    if (itemHeight <= 0f) return@drawWithContent

    // 픽셀 기반 계산 — 아이템 단위 분모는 뷰포트에 아이템이 반쯤 걸릴 때 thumb가 바닥에 닿지 않는 오차를 유발
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    val scrollableHeight = (totalItems * itemHeight - viewportHeight).coerceAtLeast(1f)
    val scrolledHeight = state.firstVisibleItemIndex * itemHeight + state.firstVisibleItemScrollOffset
    val scrollProgress = (scrolledHeight / scrollableHeight).coerceIn(0f, 1f)

    val thumbHeight = size.height * thumbHeightFraction
    val thumbOffset = (size.height - thumbHeight) * scrollProgress

    drawRect(
        color = thumbColor,
        topLeft = Offset(x = size.width - thumbWidthPx, y = thumbOffset),
        size = Size(width = thumbWidthPx, height = thumbHeight)
    )
}
