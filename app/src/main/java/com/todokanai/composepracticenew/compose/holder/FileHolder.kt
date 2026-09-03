package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.todokanai.composepracticenew.compose.presets.image.ImageHolder
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/** 파일 목록의 개별 항목을 표시하는 컴포저블. modifier.background 처리는 FileListView에서 담당 예정. */
@Composable
fun FileHolder(
    modifier: Modifier,
    file: FileHolderItem,
    isSelected: Boolean,
    icon: Painter,
    isAsyncImage: Boolean
) {
    // data URI 문자열이 바뀔 때만 Uri 객체 재생성 — 매 리컴포지션마다 toUri() 호출 방지
    val imageData = remember(file.data) { file.data?.toUri() }

    Row(
        modifier = modifier
            .background(if (isSelected) Color.LightGray else Color.Transparent)
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImageHolder(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight()
                .padding(5.dp),
            isAsyncImage = isAsyncImage,
            data = imageData,
            icon = icon
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
