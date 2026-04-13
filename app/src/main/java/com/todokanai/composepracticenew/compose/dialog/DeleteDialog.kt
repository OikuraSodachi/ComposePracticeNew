package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.compose.presets.dialog.BooleanDialog

@Composable
fun DeleteDialog(
    onConfirm: ()->Unit,
    onCancel:()->Unit
){
    BooleanDialog(
        modifier = Modifier,
        onConfirm = onConfirm,
        onCancel = onCancel,
        title = "",
        message = "Delete?"
    )
}