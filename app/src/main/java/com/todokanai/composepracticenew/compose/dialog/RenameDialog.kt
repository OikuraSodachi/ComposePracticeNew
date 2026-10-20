package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog

@Composable
fun RenameDialog(
    onConfirm: (text:String) -> Unit,
    onDismiss: () -> Unit
){
    EditTextDialog(
        modifier = Modifier,
        title = stringResource(R.string.dialog_rename_title),
        defaultText = stringResource(R.string.dialog_rename_hint),
        onConfirm = {onConfirm(it)},
        onDismiss = {onDismiss()}
    )
}

@Preview
@Composable
private fun RenameDialogPreview(){
    RenameDialog(
        onConfirm = {},
        onDismiss = {}
    )
}