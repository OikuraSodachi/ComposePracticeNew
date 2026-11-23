package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todokanai.composepracticenew.compose.presets.image.ImageHolder
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.FileType
import com.todokanai.composepracticenew.ui.thumbnail.Thumbnail

/** 숨김 항목(이름이 점으로 시작)의 아이콘 불투명도. placeholder와 불러온 썸네일 모두 흐리게 하고 텍스트는 그대로 둔다. */
private const val HIDDEN_ICON_ALPHA = 0.4f

/**
 * 파일 행의 왼쪽 부분 — 아이콘·썸네일, 그 오른쪽에 이름과 이름 아래 한 줄([subtitle]). 남은 너비를 이름 칸이 차지한다.
 * 목록 행과 검색 결과 행이 아이콘 크기·숨김 표시·이름 모양을 함께 쓰도록 한 곳에 둔다.
 * @param item 표시할 항목
 * @param typeIcons 항목 종류별 placeholder 아이콘 — 목록 전체가 공유하는 같은 인스턴스여야 행 리컴포지션이 생략된다
 * @param subtitle 이름 아래 줄을 그리는 컴포저블(목록 행은 수정 시각, 검색 결과는 위치)
 */
@Composable
fun RowScope.FileIconAndName(item: FileHolderItem, typeIcons: Map<FileType, Painter>, subtitle: @Composable () -> Unit) {
    FileIconAndName(
        name = item.name,
        icon = typeIcons.getValue(item.fileType),
        thumbnail = item.thumbnail,
        isHidden = item.isHidden,
        subtitle = subtitle
    )
}

/**
 * 파일 행의 왼쪽 부분을 표시 값으로 그린다. 파일 목록 항목이 아닌 행(압축 파일 안의 항목)도 같은 모양을 쓰도록 둔다.
 * @param name 항목 이름
 * @param icon 썸네일이 없거나 불러오기 전에 보이는 placeholder 아이콘
 * @param thumbnail 불러올 썸네일의 요청 모델. 없으면 null
 * @param isHidden 숨김 항목이면 true — 아이콘을 흐리게 한다
 * @param subtitle 이름 아래 줄을 그리는 컴포저블
 */
@Composable
fun RowScope.FileIconAndName(name: String, icon: Painter, thumbnail: Thumbnail?, isHidden: Boolean, subtitle: @Composable () -> Unit) {
    ImageHolder(
        modifier = Modifier
            .width(AppConstants.THUMBNAIL_SIZE_DP)
            .fillMaxHeight()
            .padding(5.dp)
            .alpha(if (isHidden) HIDDEN_ICON_ALPHA else 1f),
        data = thumbnail,
        icon = icon
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontSize = 18.sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp)
        )
        subtitle()
    }
}
