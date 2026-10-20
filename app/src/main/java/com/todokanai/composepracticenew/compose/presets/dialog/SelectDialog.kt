package com.todokanai.composepracticenew.compose.presets.dialog

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

/**
 * 선택지 중 하나를 라디오 버튼으로 고르는 범용 다이얼로그.
 *
 * 선택 상태는 호출부가 소유하며, 이 다이얼로그는 [selected]를 표시하고 선택 이벤트를 [onSelect]로만 올려보낸다.
 * 항목을 누르는 즉시 선택이 반영되므로 확정할 값이 없어 확인 버튼을 두지 않고, 하단 버튼은 닫기 하나뿐이다.
 */
@Composable
fun <T> SelectDialog(
    modifier : Modifier,
    title:String ?= null,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = stringResource(R.string.btn_dismiss)
){
    ConfirmCancelDialog(
        modifier = modifier,
        title = title,
        onConfirm = null,
        onDismiss = onDismiss,
        dismissText = dismissText
    ) {
        LazyColumn(modifier = Modifier.selectableGroup()) {
            items(options) { option ->
                SelectOptionRow(
                    label = label(option),
                    isSelected = option == selected,
                    onSelect = { onSelect(option) }
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
private fun SelectOptionRow(
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

@Preview
@Composable
private fun SelectDialogPreview(){
    SelectDialog(
        modifier = Modifier,
        title = null,
        options = listOf("Item1", "Item2"),
        selected = "Item2",
        label = { it },
        onSelect = {},
        onDismiss = {}
    )
}
