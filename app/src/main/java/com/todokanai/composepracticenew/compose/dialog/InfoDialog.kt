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
import com.todokanai.composepracticenew.ui.model.SelectionSummary


/**
 * 선택한 항목의 개수와 전체 크기를 보여주는 정보 다이얼로그. 확인할 동작이 없으므로 닫기 버튼만 둔다.
 *
 * 개수·크기 계산은 ViewModel이 [summary]로 미리 해 두고, 이 다이얼로그는 표시만 담당한다.
 */
@Composable
fun InfoDialog(
    summary: SelectionSummary,
    onDismiss: () -> Unit
) {
    val selectedNumber = stringResource(R.string.dialog_info_selected_count, summary.count)
    val sizeText: String = summary.totalSizeText

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
            summary = SelectionSummary.from(emptyList())
        ) {}
    }
}