package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState

/**
 * 다중 선택 중 목록 위에 모두 선택 체크박스와 선택한 항목 수를 보인다. 누르면 목록이 모두 선택돼 있을 때는 모두 해제하고, 아니면 목록 전체를 선택한다.
 * @param items 지금 보이는 목록의 항목 — 모두 선택의 대상
 * @param selectedList 선택 목록을 반환하는 함수
 * @param onReplaceSelection 다중 선택 목록을 바꾸는 콜백
 */
@Composable
fun SelectAllBar(
    modifier: Modifier,
    items: List<FileHolderItem>,
    selectedList: () -> List<FileHolderItem>,
    onReplaceSelection: (List<FileHolderItem>) -> Unit
) {
    // items·selectedList를 키로 — 목록이나 선택 출처가 바뀌면 다시 만든다. derivedStateOf는 selectedList()가 읽는 선택 목록 State를 추적해 체크박스 상태가 바뀔 때만 리컴포지션한다
    val toggleState by remember(items, selectedList) {
        derivedStateOf { selectAllStateOf(items, selectedList()) }
    }
    // selectedList를 키로 — 선택 목록 State를 추적해 선택한 수가 바뀔 때만 리컴포지션한다
    val selectedCount by remember(selectedList) { derivedStateOf { selectedList().size } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .triStateToggleable(
                state = toggleState,
                role = Role.Checkbox,
                onClick = { onReplaceSelection(selectionAfterSelectAllToggle(items, selectedList())) }
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(state = toggleState, onClick = null)
        Text(text = stringResource(R.string.select_all))
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.selected_count, selectedCount),
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

/**
 * 모두 선택의 대상인, 지금 보이는 목록의 항목.
 * @param listing 지금 목록 상태
 * @return 항목을 보여 주는 상태면 그 항목, 아니면(조회 중·실패) 빈 목록
 */
internal fun selectAllTargetsOf(listing: ListingUiState): List<FileHolderItem> =
    (listing as? ListingUiState.Loaded)?.items.orEmpty()

/**
 * 모두 선택 체크박스의 상태.
 * @param items 지금 보이는 목록의 항목
 * @param selected 선택 목록
 * @return 목록이 비어 있지 않고 모두 선택돼 있으면 On, 하나도 선택되지 않았으면 Off, 일부만 선택돼 있으면 Indeterminate
 */
internal fun selectAllStateOf(items: List<FileHolderItem>, selected: List<FileHolderItem>): ToggleableState {
    val selectedPaths = selected.mapTo(HashSet()) { it.path }
    val selectedInList = items.count { it.path in selectedPaths }
    return when {
        selectedInList == 0 -> ToggleableState.Off
        selectedInList == items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

/**
 * 모두 선택 체크박스를 눌렀을 때의 새 선택 목록.
 * @param items 지금 보이는 목록의 항목
 * @param selected 지금 선택 목록
 * @return 목록이 모두 선택돼 있으면 빈 목록(모두 해제), 아니면 목록 전체
 */
internal fun selectionAfterSelectAllToggle(items: List<FileHolderItem>, selected: List<FileHolderItem>): List<FileHolderItem> =
    if (selectAllStateOf(items, selected) == ToggleableState.On) emptyList() else items
