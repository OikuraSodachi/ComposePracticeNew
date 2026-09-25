package com.todokanai.composepracticenew.compose.presets.image

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.todokanai.composepracticenew.myobjects.AppConstants

/** 파일 썸네일 또는 비동기 이미지를 표시하는 이미지 컴포저블. */
@Composable
fun ImageHolder(
    modifier: Modifier,
    isAsyncImage: Boolean,
    icon: Painter,
    data: Any?
) {
    if (isAsyncImage) {
        val context = LocalContext.current
        val sizePx = with(LocalDensity.current) { AppConstants.THUMBNAIL_SIZE_DP.roundToPx() }
        // data 또는 sizePx가 바뀔 때만 재빌드 — sizePx를 키에 포함해 밀도 변경 시 stale 요청 재사용 방지
        val imageRequest = remember(data, sizePx) {
            ImageRequest.Builder(context)
                .data(data)
                .size(sizePx, sizePx)
                // crossfade 없음 — 스크롤 중 다수 이미지 동시 페이드 애니메이션 경합 방지를 위해 의도적으로 제거
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = modifier,
            placeholder = icon
        )
    } else {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = modifier
        )
    }
}
