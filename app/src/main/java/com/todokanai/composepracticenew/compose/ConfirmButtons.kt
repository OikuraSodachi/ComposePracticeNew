package com.todokanai.composepracticenew.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.ConfirmMode
import com.todokanai.composepracticenew.ui.model.SelectMode

/** 확인 모드에서 취소 버튼과, 모드가 보유한 라벨을 표시하는 확인 버튼을 제공한다. */
@Composable
fun ConfirmButtons(
    modifier: Modifier,
    confirm:()->Unit,
    cancel:()->Unit,
    mode: ConfirmMode
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        TextButton(
            onClick = cancel,
            Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.btn_cancel))
        }
        TextButton(
            onClick = confirm,
            Modifier.weight(1f)
        ) {
            Text(stringResource(mode.labelRes))
        }
    }
}

@Preview
@Composable
private fun ConfirmButtonsPreview(){
    Surface() {
        ConfirmButtons(
            modifier = Modifier,
            confirm = {},
            cancel = {},
            mode = SelectMode.LocalOperation.Unzip
        )
    }
}
