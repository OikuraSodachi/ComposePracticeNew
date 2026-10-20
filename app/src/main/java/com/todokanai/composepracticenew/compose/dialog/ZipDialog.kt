package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.model.FileNameError

/** 압축 파일 이름(확장자 제외)을 입력받는 다이얼로그. 이름 규칙은 [validate]로 주입받는다. */
@Composable
fun ZipDialog(
    onConfirm: (text:String)->Unit,
    onDismiss: ()-> Unit,
    validate: (String) -> Set<FileNameError>

){
    EditTextDialog(
        modifier = Modifier,
        title = stringResource(R.string.dialog_zip_title),
        defaultText = stringResource(R.string.dialog_zip_hint),
        validate = validate,
        errorMessage = { fileNameErrorMessage(it) },
        onConfirm = { onConfirm(it) },
        onDismiss = {onDismiss()}
    )
}

/** ZipDialog의 빈 입력 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun ZipDialogPreview(){
    ZipDialog(
        onConfirm = {},
        onDismiss = {},
        validate = { emptySet() }
    )
}
