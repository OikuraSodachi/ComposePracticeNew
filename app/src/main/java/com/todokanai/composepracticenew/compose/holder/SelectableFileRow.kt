package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 선택할 수 있는 목록 행의 틀 — 선택 강조 배경, 왼쪽의 아이콘·이름 칸([iconAndName]), 오른쪽 아래의 양 칸([amount]).
 * 파일 목록 행과 압축 파일 내용 보기 행이 같은 모양과 선택 표시를 쓰도록 한 곳에 둔다.
 * 길게 누르기는 받지 않는다 — 목록의 드래그 선택(dragSelectGesture)이 맡으며, 행이 길게 누르기를 감지하면 그 뒤 이벤트를 모두 소비해 목록의 드래그가 취소된다.
 * @param modifier 행의 배치를 정하는 Modifier
 * @param isSelected 선택된 행이면 true — 배경을 강조한다
 * @param clickEnabled 행을 누를 수 있으면 true
 * @param onClick 행을 눌렀을 때의 콜백
 * @param amount 오른쪽 아래 칸의 문구(크기, 하위 항목 수 등). 비어 있으면 빈 칸이다
 * @param iconAndName 왼쪽의 아이콘·이름 칸을 그리는 컴포저블([FileIconAndName])
 */
@Composable
fun SelectableFileRow(
    modifier: Modifier,
    isSelected: Boolean,
    clickEnabled: Boolean,
    onClick: () -> Unit,
    amount: String,
    iconAndName: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clickable(enabled = clickEnabled, onClick = onClick)
            .background(if (isSelected) Color.LightGray else Color.Transparent)
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconAndName()

        Text(
            text = amount,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(4.dp)
        )
    }
}

/**
 * [SelectableFileRow]의 이름 아래 줄 문구.
 * @param text 표시할 문구(수정 시각 등)
 */
@Composable
fun FileRowSubtitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, end = 4.dp)
    )
}
