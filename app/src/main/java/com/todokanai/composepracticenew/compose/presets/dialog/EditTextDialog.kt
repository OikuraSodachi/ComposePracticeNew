package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R

@Composable
fun EditTextDialog(
    modifier: Modifier,
    title: String,
    defaultText:String,
    cancelText: String? = null,
    confirmText: String? = null,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val resolvedCancelText = cancelText ?: stringResource(R.string.btn_cancel)
    val resolvedConfirmText = confirmText ?: stringResource(R.string.btn_confirm)
    var text by remember { mutableStateOf("") }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onCancel,
        title = { Text(text = title) },
        text = {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = text,
                placeholder = {Text(defaultText)},
                onValueChange = { text = it },
                singleLine = true
            )
               },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(text)
                    onCancel()
                }
            ) {
                Text(text = resolvedConfirmText)
            }
                        },
        dismissButton = {
            Button(
                onClick = {
                    onCancel()
                }
            ) {
                Text(text = resolvedCancelText)
            }
        }
    )
}

@Preview
@Composable
private fun EditTextDialogPreview(){
    EditTextDialog(
        modifier = Modifier,
        title = "title",
        defaultText = "default text",
        onConfirm = {},
        onCancel = {}
    )
}