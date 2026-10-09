package com.todokanai.composepracticenew.compose.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LazyList 오른쪽 끝에 드래그 가능한 세로 스크롤바 thumb를 overlay로 그리는 Modifier.
 * Box 안에 align(Alignment.CenterEnd)로 배치된 좁은 Spacer에 적용해야 함 —
 * 스크롤바 전용 strip이 hit-testing을 담당하므로 LazyColumn 스크롤 제스처와 충돌하지 않음.
 *
 * @param state 연결할 [LazyListState]
 * @param thumbColor thumb 색상
 * @param thumbWidth thumb 시각적 너비
 * @param minThumbHeightFraction 전체 높이 대비 thumb 최소 비율
 * @return 스크롤바 thumb가 오른쪽에 overlay로 그려진 Modifier
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    thumbColor: Color = Color.Gray.copy(alpha = 0.6f),
    thumbWidth: Dp = 8.dp,
    minThumbHeightFraction: Float = 0.05f
): Modifier = this
    .pointerInput(state) {
        awaitEachGesture {
            // Initial pass 사용 — LazyColumn의 scrollable이 Initial pass에서 수직 드래그를 선점하므로
            // 같은 pass에서 먼저 소비해야 scrollbar drag가 LazyColumn scroll보다 우선됨
            val down = awaitFirstDown(pass = PointerEventPass.Initial)

            // thumb가 그려지지 않는 상태(스크롤 불필요)면 소비하지 않고 흘려보냄 —
            // drawWithContent의 thumbHeightFraction >= 1f 가드와 입력 조건을 일치시킴
            val infoAtDown = state.layoutInfo
            val totalAtDown = infoAtDown.totalItemsCount
            val visibleAtDown = infoAtDown.visibleItemsInfo
            if (visibleAtDown.isEmpty() || totalAtDown <= 0) return@awaitEachGesture
            val fractionAtDown = (visibleAtDown.size.toFloat() / totalAtDown)
                .coerceIn(minThumbHeightFraction, 1f)
            if (fractionAtDown >= 1f) return@awaitEachGesture

            down.consume()
            var lastY = down.position.y

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.find { it.id == down.id } ?: break
                if (!change.pressed) break

                val dragDeltaY = change.position.y - lastY
                lastY = change.position.y
                change.consume()

                val layoutInfo = state.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isNotEmpty() && totalItems > 0) {
                    val thumbHeightFraction = (visibleItems.size.toFloat() / totalItems)
                        .coerceIn(minThumbHeightFraction, 1f)
                    if (thumbHeightFraction >= 1f) break

                    val itemHeight = visibleItems.first().size.toFloat()
                    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                    val scrollableHeight = (totalItems * itemHeight - viewportHeight).coerceAtLeast(1f)
                    val thumbTrackHeight = (size.height * (1f - thumbHeightFraction)).coerceAtLeast(1f)

                    // thumb 이동 거리를 콘텐츠 스크롤 픽셀로 변환 — 두 공간의 비율 적용
                    state.dispatchRawDelta(dragDeltaY * scrollableHeight / thumbTrackHeight)
                }
            }
        }
    }
    .drawWithContent {
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
