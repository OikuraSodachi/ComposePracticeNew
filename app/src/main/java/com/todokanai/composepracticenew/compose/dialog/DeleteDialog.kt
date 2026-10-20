package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.BooleanDialog

@Composable
fun DeleteDialog(
    onConfirm: ()->Unit,
    onDismiss:()->Unit
){
    BooleanDialog(
        modifier = Modifier,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = null,
        message = stringResource(R.string.dialog_delete_message)
    )
}