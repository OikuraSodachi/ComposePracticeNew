package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.SelectDialog
import com.todokanai.composepracticenew.model.SortMode

/**
 * 정렬 기준을 고르는 다이얼로그.
 *
 * 선택지는 [SortMode] 선언 순서대로 표시한다.
 * 선택은 즉시 반영되어 되돌릴 수 없으므로, 하단 버튼은 '취소'가 아닌 '닫기' 라벨을 쓴다.
 */
@Composable
fun SortDialog(
    selected: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit
){
    SelectDialog(
        modifier = Modifier,
        title = null,
        options = SortMode.entries,
        selected = selected,
        label = { sortModeLabel(it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.btn_dismiss),
        onConfirm = null
    )
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

@Preview
@Composable
private fun SortDialogPreview(){
    SortDialog(
        selected = SortMode.BY_NAME_ASCENDING,
        onSelect = {},
        onDismiss = {}
    )
}
