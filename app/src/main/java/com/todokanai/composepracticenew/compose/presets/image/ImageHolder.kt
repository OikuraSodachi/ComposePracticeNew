package com.todokanai.composepracticenew.compose.presets.image

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
        // data가 바뀔 때만 재빌드 — 매 리컴포지션마다 Coil 요청이 새로 생성되는 것을 방지
        val imageRequest = remember(data) {
            ImageRequest.Builder(context)
                .data(data)
                .crossfade(true)
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
