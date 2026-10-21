package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileNameError

/** 파일·폴더의 새 이름을 입력받는 다이얼로그. 이름 규칙은 [validate]로 주입받는다. */
@Composable
fun RenameDialog(
    onConfirm: (text:String) -> Unit,
    onDismiss: () -> Unit,
    validate: (String) -> Set<FileNameError>
){
    FileNameDialog(
        title = stringResource(R.string.dialog_rename_title),
        hint = stringResource(R.string.dialog_rename_hint),
        validate = validate,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** RenameDialog의 빈 입력 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun RenameDialogPreview(){
    RenameDialog(
        onConfirm = {},
        onDismiss = {},
        validate = { emptySet() }
    )
}
