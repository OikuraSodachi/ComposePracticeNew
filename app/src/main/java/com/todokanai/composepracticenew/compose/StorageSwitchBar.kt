package com.todokanai.composepracticenew.compose

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R

/**
 * 로컬/원격 스토리지 뷰를 전환하는 탭 바. isRemote가 true이면 원격 탭이 선택된 상태로 표시된다.
 * @param remoteEnabled 원격 탭을 누를 수 있는지. false면 원격 탭이 비활성으로 보이고 누름에 반응하지 않는다(연결된 원격 세션이 없을 때, #523)
 */
@Composable
fun StorageSwitchBar(
    modifier: Modifier = Modifier,
    isRemote: Boolean,
    remoteEnabled: Boolean,
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
            enabled = remoteEnabled,
            text = { Text(stringResource(R.string.tab_remote)) }
        )
    }
}
