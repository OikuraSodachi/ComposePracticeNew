package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td


@Composable
fun InfoDialog(
    files: List<FileHolderItem>,
    onCancel: () -> Unit
) {
    val selectedNumber = stringResource(R.string.dialog_info_selected_count, files.size)
    val sizeText: String = readableFileSize_td(files.sumOf { it.sizeBytes })

    AlertDialog(
        onDismissRequest = {onCancel()},
        title = {Text("")},
        text= {
              InfoChart(
                  modifier = Modifier,
                  selectedNumber = selectedNumber,
                  sizeText = sizeText
              )
        },
        dismissButton = {          Button(
            modifier = Modifier
                .fillMaxWidth(),
            onClick = {onCancel()}
        ){
            Text(text = stringResource(R.string.btn_dismiss))
        }},
        confirmButton = {}
    )
}

@Composable
private fun InfoColumn(
    modifier: Modifier,
    title:String,
    info:String
){
    Column(
        modifier = modifier
            .fillMaxWidth()
    ){
        Text(title)
        Text(info)
    }
}

@Composable
private fun InfoChart(
    modifier: Modifier,
    selectedNumber:String,
    sizeText:String
){
    Column(
        modifier = modifier
    ) {
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_selected),
            info = selectedNumber
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_size),
            info = sizeText
        )
    }
}

@Preview
@Composable
private fun InfoDialogPreview(){
    Surface() {
        InfoDialog(
            files = emptyList()
        ) {}
    }
}