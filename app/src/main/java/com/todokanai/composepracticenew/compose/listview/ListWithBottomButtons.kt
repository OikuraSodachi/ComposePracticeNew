package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 목록 영역 아래쪽에 하단 작업 버튼을 겹쳐 그린다. 목록은 버튼 유무와 관계없이 영역 전체를 차지하고, 버튼 높이는 목록 안쪽 아래 여백으로만 받는다.
 * 버튼이 목록 크기를 줄이면 다중 선택으로 들어가는 순간 진행 중인 드래그 선택의 기준(목록 위치·크기)이 바뀌어, 손가락을 끈 것처럼 처리되기 때문이다(#520).
 * @param modifier 영역의 크기·배치를 정하는 Modifier
 * @param buttons 하단 작업 버튼. 모드에 따라 아무것도 그리지 않을 수 있으며, 그때 아래 여백은 0이다
 * @param content 목록을 그리는 컴포저블. 받은 여백을 목록 안쪽 여백으로 써서 마지막 행을 버튼 위까지 스크롤할 수 있게 한다
 */
@Composable
fun ListWithBottomButtons(
    modifier: Modifier,
    buttons: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    // Scaffold는 bottomBar를 내용 위에 겹쳐 놓고 그 높이를 같은 프레임에 내용의 여백으로 넘긴다. 시스템 영역 여백은 화면 호스트가 이미 처리하므로 더하지 않는다
    Scaffold(
        modifier = modifier,
        bottomBar = {
            // 뒤로 지나가는 행이 비치지 않도록 화면 배경색으로 칠한다
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) { buttons() }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content
    )
}
