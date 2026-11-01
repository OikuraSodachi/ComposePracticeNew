package com.todokanai.composepracticenew.compose.holder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.FileType
import com.todokanai.composepracticenew.ui.model.SearchResult

/**
 * 검색 결과 항목 하나를 표시하는 컴포저블. 목록 행과 같은 아이콘·썸네일과 이름([FileIconAndName])을 보이고, 이름 아래에 항목이 들어 있는 폴더의 경로를,
 * 오른쪽에 그 폴더로 이동하는 위치 열기 버튼을 둔다.
 * @param modifier 행의 배치를 정하는 Modifier
 * @param result 표시할 검색 결과
 * @param typeIcons 항목 종류별 placeholder 아이콘 — 결과 목록 전체가 공유하는 같은 인스턴스여야 행 리컴포지션이 생략된다
 * @param onClick 행을 눌렀을 때의 콜백
 * @param onOpenLocation 위치 열기 버튼을 눌렀을 때의 콜백
 */
@Composable
fun SearchResultHolder(
    modifier: Modifier,
    result: SearchResult,
    typeIcons: Map<FileType, Painter>,
    onClick: () -> Unit,
    onOpenLocation: () -> Unit
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIconAndName(item = result.item, typeIcons = typeIcons) {
            Text(
                text = result.location.orEmpty(),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, end = 4.dp)
            )
        }

        // 상위 폴더가 없으면 열 위치가 없으므로 버튼을 두지 않는다
        if (result.location != null) {
            TextButton(onClick = onOpenLocation) {
                Text(text = stringResource(R.string.btn_open_location))
            }
        }
    }
}
