package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog

/**
 * 저장된 접속 정보가 잘못돼(범위를 벗어난 포트 등) 연결을 시도하지 않았음을 알리는 다이얼로그. 다시 시도해도 되지 않으므로 접속 정보를 고치도록 안내하고 확인 버튼만 둔다.
 * @param onDismiss 사용자가 다이얼로그를 닫을 때 실행할 콜백
 */
@Composable
fun InvalidServerSettingsDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_invalid_server_settings_title)) },
        text = { Text(stringResource(R.string.dialog_invalid_server_settings_message)) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_confirm))
            }
        }
    )
}
