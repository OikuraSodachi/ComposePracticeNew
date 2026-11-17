package com.todokanai.composepracticenew.compose.presets.checkbox

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * 라벨을 붙인 체크박스. 라벨을 포함한 줄 전체가 하나의 체크박스로 동작한다 —
 * 라벨을 눌러도 체크가 바뀌고, TalkBack이 라벨과 체크 상태를 한 항목으로 읽는다.
 * 체크박스 자체는 클릭을 받지 않는다(줄과 따로 받으면 포커스가 둘로 나뉜다).
 * @param checked 체크했는지
 * @param onCheckedChange 체크 상태가 바뀔 때의 콜백
 * @param label 체크박스 옆에 보일 라벨
 * @param modifier 줄에 적용할 Modifier
 */
@Composable
fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox)
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
