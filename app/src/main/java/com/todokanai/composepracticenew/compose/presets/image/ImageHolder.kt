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
import com.todokanai.composepracticenew.ui.thumbnail.Thumbnail

/**
 * 파일 썸네일 또는 종류 아이콘을 표시하는 이미지 컴포저블. 썸네일을 불러오는 중이거나 불러오지 못하면 [icon]을 표시한다.
 * @param data 불러올 썸네일 요청 모델. null이면 썸네일 없이 [icon]만 표시한다
 */
@Composable
fun ImageHolder(
    modifier: Modifier,
    icon: Painter,
    data: Thumbnail?
) {
    if (data != null) {
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
            placeholder = icon,
            // 디코딩 실패(손상·미지원 형식) 시에도 빈 칸이 아닌 종류 아이콘을 남긴다
            error = icon
        )
    } else {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = modifier
        )
    }
}
