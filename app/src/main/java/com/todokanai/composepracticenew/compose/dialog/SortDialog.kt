package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.compose.presets.dialog.SelectDialog

@Composable
fun SortDialog(
    items : List<Pair<String,()->Unit>>,
    selectedItem : String,
    onCancel:()->Unit
){
    SelectDialog(
        modifier = Modifier,
        title = null,
        items = items,
        selectedItem = selectedItem,
        onDismissRequest = {onCancel()},
        onConfirm = null,
        onCancel = {onCancel()}
    )
}

@Preview
@Composable
private fun SortDialogPreview(){
    SortDialog(
        items = listOf(Pair("1",{}),Pair("2",{})),
        selectedItem = "2",
        onCancel = {}
    )
}