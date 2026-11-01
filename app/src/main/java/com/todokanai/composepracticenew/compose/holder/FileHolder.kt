package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.FileType

/**
 * 파일 목록의 개별 항목을 표시하는 컴포저블. 아이콘과 썸네일 여부는 [file]의 종류로 정하고, 숨김 항목은 아이콘을 흐리게 표시한다.
 * 오른쪽 아래 칸에는 파일이면 크기를, 폴더면 하위 항목 수를 표시한다.
 * @param typeIcons 항목 종류별 placeholder 아이콘 — 목록 전체가 공유하는 같은 인스턴스여야 행 리컴포지션이 생략된다
 * @param childCount 폴더의 하위 항목 수 표시 상태. 파일이거나 하위 항목 수를 세지 않는 목록(원격)이면 null이며, 그때는 크기 칸을 그대로 표시한다
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileHolder(
    modifier: Modifier,
    file: FileHolderItem,
    isSelected: Boolean,
    typeIcons: Map<FileType, Painter>,
    childCount: ChildCountInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) Color.LightGray else Color.Transparent)
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIconAndName(item = file, typeIcons = typeIcons) {
            Text(
                text = file.lastModified,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, end = 4.dp)
            )
        }

        Text(
            text = amountText(file, childCount),
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(4.dp)
        )
    }
}

/**
 * 오른쪽 아래 칸에 표시할 항목의 양. 하위 항목 수 표시 상태가 없으면 [file]의 크기이고, 있으면 하위 항목 수다.
 * 세는 중이면 비워 두고, 읽지 못했으면 0개로 보이지 않도록 읽지 못함을 적는다.
 */
@Composable
private fun amountText(file: FileHolderItem, childCount: ChildCountInfo?): String = when (childCount) {
    null -> file.size
    ChildCountInfo.Loading -> ""
    is ChildCountInfo.Known -> stringResource(R.string.child_count, childCount.count)
    is ChildCountInfo.Failed -> stringResource(R.string.read_failed)
}
