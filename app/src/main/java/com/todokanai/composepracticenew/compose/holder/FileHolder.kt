package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.todokanai.composepracticenew.compose.presets.image.ImageHolder
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.FileType

/** 숨김 항목(이름이 점으로 시작)의 아이콘 불투명도. placeholder와 불러온 썸네일 모두 흐리게 하고 텍스트는 그대로 둔다. */
private const val HIDDEN_ICON_ALPHA = 0.4f

/**
 * 파일 목록의 개별 항목을 표시하는 컴포저블. 아이콘과 썸네일 여부는 [file]의 종류로 정하고, 숨김 항목은 아이콘을 흐리게 표시한다.
 * @param typeIcons 항목 종류별 placeholder 아이콘 — 목록 전체가 공유하는 같은 인스턴스여야 행 리컴포지션이 생략된다
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileHolder(
    modifier: Modifier,
    file: FileHolderItem,
    isSelected: Boolean,
    typeIcons: Map<FileType, Painter>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // data URI 문자열이 바뀔 때만 Uri 객체 재생성 — 매 리컴포지션마다 toUri() 호출 방지
    val imageData = remember(file.data) { file.data?.toUri() }

    Row(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) Color.LightGray else Color.Transparent)
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImageHolder(
            modifier = Modifier
                .width(AppConstants.THUMBNAIL_SIZE_DP)
                .fillMaxHeight()
                .padding(5.dp)
                .alpha(if (file.isHidden) HIDDEN_ICON_ALPHA else 1f),
            isAsyncImage = file.hasImagePreview,
            data = imageData,
            icon = typeIcons.getValue(file.fileType)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = file.name,
                fontSize = 18.sp,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp)
            )
            Text(
                text = file.lastModified,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, end = 4.dp)
            )
        }

        Text(
            text = file.size,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(4.dp)
        )
    }
}
