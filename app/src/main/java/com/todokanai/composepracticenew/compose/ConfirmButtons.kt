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
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_COPY
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_DOWNLOAD
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_UPLOAD

@Composable
fun ConfirmButtons(
    modifier: Modifier,
    confirm:()->Unit,
    cancel:()->Unit,
    mode:Int
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        TextButton(
            onClick = { cancel() },
            Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.btn_cancel))
        }
        TextButton(
            onClick = { confirm() },
            Modifier.weight(1f)
        ) {
            when(mode){
                CONFIRM_MODE_MOVE ->{
                    Text(stringResource(R.string.btn_move))
                }
                CONFIRM_MODE_COPY ->{
                    Text(stringResource(R.string.btn_copy))
                }
                CONFIRM_MODE_UNZIP ->{
                    Text(stringResource(R.string.btn_unzip))
                }
                CONFIRM_MODE_DOWNLOAD -> {
                    Text(stringResource(R.string.btn_download))
                }
                CONFIRM_MODE_UPLOAD -> {
                    Text(stringResource(R.string.btn_upload_confirm))
                }
            }
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
            mode = CONFIRM_MODE_UNZIP
        )
    }
}