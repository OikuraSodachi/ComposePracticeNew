package com.todokanai.composepracticenew.compose.presets.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 잘린 텍스트의 끝쪽을 흐리게 그리는 폭. */
private val END_FADE_WIDTH = 24.dp

/**
 * 한 줄로 표시하고, 너비를 넘어 잘리면 가로로 계속 흐르며 끝쪽을 흐리게 그려 잘렸다는 것을 보여주는 텍스트.
 * 너비 안에 들어오는 텍스트는 움직이지도 흐려지지도 않는다.
 * 흐름은 시스템의 애니메이션 배율 설정과 무관하게 계속된다(basicMarquee의 동작).
 * @param text 표시할 텍스트
 * @param style 텍스트 스타일
 * @param color 글자색. [Color.Unspecified]면 [style]의 색을 쓴다
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    // 키 없음 — 텍스트 전체 폭은 매 레이아웃마다 onTextLayout이 갱신하고, 그리기 단계에서만 읽어 넘침이 바뀌어도 다시 그리기만 한다
    // mutableIntStateOf는 이 프로젝트의 compose runtime에 없어 mutableStateOf를 쓴다
    var contentWidth by remember { mutableStateOf(0) }
    Text(
        text = text,
        modifier = modifier
            // 끝쪽 페이드를 텍스트에만 합성하도록 별도 레이어에 그린다
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (isOverflowing(contentWidth)) drawEndFade()
            }
            .basicMarquee(iterations = Int.MAX_VALUE),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        // basicMarquee가 폭 제한 없이 측정하므로 레이아웃 폭이 곧 텍스트 전체 폭이다
        onTextLayout = { contentWidth = it.size.width }
    )
}

/** 텍스트 전체 폭 [contentWidth]가 보이는 폭을 넘는지. 보이는 폭은 marquee가 너비 제한 안으로 줄인 이 그리기 영역의 폭이다. */
private fun DrawScope.isOverflowing(contentWidth: Int): Boolean = contentWidth > size.width

/** 읽는 방향의 끝쪽 [END_FADE_WIDTH]를 텍스트 쪽에서 투명 쪽으로 흐리게 지운다. 시작 쪽은 멈춘 상태의 첫 글자를 가리지 않도록 그대로 둔다. */
private fun DrawScope.drawEndFade() {
    val fadeWidth = END_FADE_WIDTH.toPx().coerceAtMost(size.width)
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val fadeStartX = if (isRtl) fadeWidth else size.width - fadeWidth
    val fadeEndX = if (isRtl) 0f else size.width
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = fadeStartX, endX = fadeEndX),
        topLeft = Offset(if (isRtl) 0f else fadeStartX, 0f),
        size = Size(fadeWidth, size.height),
        blendMode = BlendMode.DstIn
    )
}
