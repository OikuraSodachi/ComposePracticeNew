package com.todokanai.composepracticenew.compose.presets.text

import androidx.compose.foundation.DefaultMarqueeDelayMillis
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle

/** 잘린 텍스트가 한 번 표시될 때 흐르는 횟수. 끝없이 흐르지 않도록 정해 두고, 다시 보려면 탭한다. */
private const val MARQUEE_ITERATIONS = 3

/**
 * 한 줄로 표시하고, 너비를 넘어 잘리면 가로로 [MARQUEE_ITERATIONS]번 흐른 뒤 처음 위치에 멈추는 텍스트.
 * 탭하면 곧바로 다시 흐른다. 너비 안에 들어오는 텍스트는 움직이지 않는다.
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
    // 키 없음 — 이 텍스트가 표시되는 동안의 탭 횟수. 탭할 때마다 늘려 아래 key()로 흐름을 처음부터 다시 시작한다
    var replayCount by remember { mutableStateOf(0) }
    // replayCount를 키로 — 바뀌면 marquee 애니메이션을 새로 만들어 남은 반복 횟수와 위치를 처음으로 되돌린다
    key(replayCount) {
        Text(
            text = text,
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures { replayCount++ } }
                .basicMarquee(
                    iterations = MARQUEE_ITERATIONS,
                    // 탭으로 다시 흐를 때는 기다리지 않는다 — 처음 표시할 때만 읽을 시간을 준다
                    initialDelayMillis = if (replayCount == 0) DefaultMarqueeDelayMillis else 0
                ),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}
