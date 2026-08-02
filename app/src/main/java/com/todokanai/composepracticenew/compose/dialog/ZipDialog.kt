package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog

@Composable
fun ZipDialog(
    onConfirm: (text:String)->Unit,
    onCancel: ()-> Unit

){
    EditTextDialog(
        modifier = Modifier,
        title = stringResource(R.string.dialog_zip_title),
        defaultText = stringResource(R.string.dialog_zip_hint),
        onConfirm = { onConfirm(it) },
        onCancel = {onCancel()}
    )
}

@Preview
@Composable
private fun ZipDialogPreview(){
    ZipDialog(
        onConfirm = {}
    ) {}
}