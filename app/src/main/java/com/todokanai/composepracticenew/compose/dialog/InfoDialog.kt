package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td


/** 선택한 항목의 개수와 전체 크기를 보여주는 정보 다이얼로그. 확인할 동작이 없으므로 닫기 버튼만 둔다. */
@Composable
fun InfoDialog(
    files: List<FileHolderItem>,
    onDismiss: () -> Unit
) {
    val selectedNumber = stringResource(R.string.dialog_info_selected_count, files.size)
    val sizeText: String = readableFileSize_td(files.sumOf { it.sizeBytes })

    ConfirmCancelDialog(
        modifier = Modifier,
        title = null,
        onConfirm = null,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.btn_dismiss)
    ) {
        InfoChart(
            modifier = Modifier,
            selectedNumber = selectedNumber,
            sizeText = sizeText
        )
    }
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