package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R

/**
 * 서버의 파일 목록을 읽을 수 없어 연결을 종료했음을 알리는 다이얼로그. 이미 끝난 일을 알리므로 확인 버튼만 둔다.
 * @param onDismiss 사용자가 다이얼로그를 닫을 때 실행할 콜백
 */
@Composable
fun UnreadableServerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_unreadable_server_title)) },
        text = { Text(stringResource(R.string.dialog_unreadable_server_message)) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_confirm))
            }
        }
    )
}
