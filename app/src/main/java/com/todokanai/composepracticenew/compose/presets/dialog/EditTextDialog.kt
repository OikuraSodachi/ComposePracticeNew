package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R

/** 한 줄 텍스트를 입력받아 확인 시 전달하는 다이얼로그. */
@Composable
fun EditTextDialog(
    modifier: Modifier,
    title: String,
    defaultText:String,
    initialText: String = "",
    dismissText: String = stringResource(R.string.btn_cancel),
    confirmText: String = stringResource(R.string.btn_confirm),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 키 없음 — 다이얼로그가 떠 있는 동안 사용자 입력을 유지하고, initialText가 바뀌어도 입력 중인 값을 덮어쓰지 않음
    var text by remember { mutableStateOf(initialText) }
    // text를 추적 — isNotBlank 결과가 바뀔 때만 본문을 무효화해 글자 입력마다의 본문 재실행을 막음
    val isValid by remember { derivedStateOf { text.isNotBlank() } }
    ConfirmCancelDialog(
        modifier = modifier,
        title = title,
        onConfirm = { onConfirm(text) },
        onDismiss = onDismiss,
        confirmEnabled = isValid,
        confirmText = confirmText,
        dismissText = dismissText
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            placeholder = {Text(defaultText)},
            onValueChange = { text = it },
            singleLine = true
        )
    }
}

@Preview
@Composable
private fun EditTextDialogPreview(){
    EditTextDialog(
        modifier = Modifier,
        title = "title",
        defaultText = "default text",
        onConfirm = {},
        onDismiss = {}
    )
}
