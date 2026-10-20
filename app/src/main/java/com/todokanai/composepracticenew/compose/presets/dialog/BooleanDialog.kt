package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** 메시지를 보여주고 확인 또는 취소를 받는 다이얼로그. */
@Composable
fun BooleanDialog(
    modifier:Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String,
    message: String,
) {
    ConfirmCancelDialog(
        modifier = modifier,
        title = title,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(text = message)
    }
}

@Preview
@Composable
private fun BooleanDialogPreview(){
    Surface() {
        BooleanDialog(
            modifier = Modifier,
            title = "BooleanDialog",
            message = "Message",
            onConfirm = {  },
            onDismiss = { }
        )
    }
}
