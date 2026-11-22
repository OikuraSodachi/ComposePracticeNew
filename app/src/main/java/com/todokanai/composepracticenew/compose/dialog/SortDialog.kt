package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.model.SortMode

/**
 * 정렬 기준을 라디오 버튼으로 고르는 다이얼로그.
 *
 * 선택지는 [SortMode] 선언 순서대로 표시한다.
 * 선택 상태는 호출부가 소유하며, 이 다이얼로그는 [selected]를 표시하고 선택 이벤트를 [onSelect]로만 올려보낸다.
 * 항목을 누르는 즉시 선택이 반영되므로 확정할 값이 없어 확인 버튼을 두지 않고, 하단 버튼은 닫기 하나뿐이다.
 */
@Composable
fun SortDialog(
    selected: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit
){
    ConfirmCancelDialog(
        modifier = Modifier,
        title = null,
        onConfirm = null,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.btn_dismiss),
        // 선택지 목록이 LazyColumn으로 스스로 스크롤한다
        contentScrollsItself = true
    ) {
        LazyColumn(modifier = Modifier.selectableGroup()) {
            items(SortMode.entries) { mode ->
                SortOptionRow(
                    label = sortModeLabel(mode),
                    isSelected = mode == selected,
                    onSelect = { onSelect(mode) }
                )
            }
        }
    }
}

/**
 * 라디오 버튼과 라벨로 이루어진 선택지 한 줄.
 *
 * 선택 여부와 클릭 처리는 호출부가 정해서 넘긴다.
 */
@Composable
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            modifier = Modifier
                .minimumInteractiveComponentSize()
        )
        Spacer(
            modifier = Modifier
                .padding(10.dp)
        )
        Text(text = label)
    }
}

/**
 * [SortMode]를 사용자 표시 문구로 바꾼다.
 *
 * 저장용 [SortMode.id]와 분리해 두어, 문구를 바꿔도 저장값 호환에 영향이 없다.
 */
@Composable
private fun sortModeLabel(mode: SortMode): String = when (mode) {
    SortMode.BY_DEFAULT -> stringResource(R.string.sort_by_default)
    SortMode.BY_NAME_ASCENDING -> stringResource(R.string.sort_by_name_ascending)
    SortMode.BY_NAME_DESCENDING -> stringResource(R.string.sort_by_name_descending)
    SortMode.BY_SIZE_ASCENDING -> stringResource(R.string.sort_by_size_ascending)
    SortMode.BY_SIZE_DESCENDING -> stringResource(R.string.sort_by_size_descending)
    SortMode.BY_TYPE_ASCENDING -> stringResource(R.string.sort_by_type_ascending)
    SortMode.BY_TYPE_DESCENDING -> stringResource(R.string.sort_by_type_descending)
    SortMode.BY_DATE_ASCENDING -> stringResource(R.string.sort_by_date_ascending)
    SortMode.BY_DATE_DESCENDING -> stringResource(R.string.sort_by_date_descending)
}

/** SortDialog 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun SortDialogPreview(){
    SortDialog(
        selected = SortMode.BY_NAME_ASCENDING,
        onSelect = {},
        onDismiss = {}
    )
}
