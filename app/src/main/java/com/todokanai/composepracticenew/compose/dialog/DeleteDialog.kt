package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog

/** 삭제 확인 메시지를 보여주고 확인 또는 취소를 받는 다이얼로그. 제목 영역은 두지 않는다. */
@Composable
fun DeleteDialog(
    onConfirm: ()->Unit,
    onDismiss:()->Unit
){
    ConfirmCancelDialog(
        modifier = Modifier,
        title = null,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(text = stringResource(R.string.dialog_delete_message))
    }
}

/** 삭제 확인 다이얼로그 미리보기. */
@Preview
@Composable
private fun DeleteDialogPreview(){
    Surface() {
        DeleteDialog(
            onConfirm = {  },
            onDismiss = { }
        )
    }
}
