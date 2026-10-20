package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R

/**
 * 확인·취소 버튼을 갖는 다이얼로그의 공통 골격.
 *
 * 확인 버튼은 [onConfirm] 실행 후 [onDismiss]로 다이얼로그를 닫고, 취소 버튼·바깥 터치·뒤로가기는 [onDismiss]만 호출한다.
 * 버튼 라벨은 항상 값을 가지므로 라벨 없는 버튼은 만들어지지 않는다.
 * [onConfirm]이 null이면 확인 버튼을 표시하지 않고, [title]이 null이면 제목 영역 자체를 두지 않는다.
 */
@Composable
fun ConfirmCancelDialog(
    modifier: Modifier,
    title: String?,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmText: String = stringResource(R.string.btn_confirm),
    dismissText: String = stringResource(R.string.btn_cancel),
    content: @Composable () -> Unit
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = title?.let { t -> @Composable { Text(text = t) } },
        text = content,
        confirmButton = {
            onConfirm?.let {
                Button(
                    onClick = {
                        it()
                        onDismiss()
                    },
                    enabled = confirmEnabled
                ) {
                    Text(text = confirmText)
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}

@Preview
@Composable
private fun ConfirmCancelDialogPreview() {
    ConfirmCancelDialog(
        modifier = Modifier,
        title = "title",
        onConfirm = {},
        onDismiss = {}
    ) {
        Text("content")
    }
}
