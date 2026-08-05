package com.todokanai.composepracticenew.compose

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R

/** 로컬/원격 스토리지 뷰를 전환하는 탭 바. isRemote가 true이면 원격 탭이 선택된 상태로 표시된다. */
@Composable
fun StorageSwitchBar(
    modifier: Modifier = Modifier,
    isRemote: Boolean,
    onSwitchToLocal: () -> Unit,
    onSwitchToRemote: () -> Unit
) {
    TabRow(
        modifier = modifier,
        selectedTabIndex = if (isRemote) 1 else 0
    ) {
        Tab(
            selected = !isRemote,
            onClick = onSwitchToLocal,
            text = { Text(stringResource(R.string.tab_local)) }
        )
        Tab(
            selected = isRemote,
            onClick = onSwitchToRemote,
            text = { Text(stringResource(R.string.tab_remote)) }
        )
    }
}
