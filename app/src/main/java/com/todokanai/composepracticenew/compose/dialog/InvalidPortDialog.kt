package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog

/**
 * 저장된 서버의 포트가 범위를 벗어나 연결을 시도하지 않았음을 알리는 다이얼로그. 다시 시도해도 되지 않으므로 포트를 고치도록 안내하고 확인 버튼만 둔다.
 * @param onDismiss 사용자가 다이얼로그를 닫을 때 실행할 콜백
 */
@Composable
fun InvalidPortDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_invalid_port_title)) },
        text = { Text(stringResource(R.string.dialog_invalid_port_message)) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_confirm))
            }
        }
    )
}
