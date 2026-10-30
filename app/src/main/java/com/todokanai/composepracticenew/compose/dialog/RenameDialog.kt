package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileNameError

/**
 * 파일·폴더의 새 이름을 입력받는 다이얼로그. 현재 이름을 채워 열고, 파일이면 확장자 앞까지만 선택한다. 이름 규칙은 [validate]로 주입받는다.
 * @param currentName 이름을 바꿀 항목의 현재 이름
 * @param isDirectory 이름을 바꿀 항목이 폴더이면 true — 폴더 이름의 점은 확장자가 아니므로 전체를 선택한다
 * @param onConfirm 확인 시 입력 이름을 전달받는 콜백
 * @param onDismiss 다이얼로그를 닫는 콜백
 * @param validate 입력 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 */
@Composable
fun RenameDialog(
    currentName: String,
    isDirectory: Boolean,
    onConfirm: (text:String) -> Unit,
    onDismiss: () -> Unit,
    validate: (String) -> Set<FileNameError>
){
    FileNameDialog(
        title = stringResource(R.string.dialog_rename_title),
        hint = stringResource(R.string.dialog_rename_hint),
        initialText = currentName,
        selectBaseNameOnly = !isDirectory,
        validate = validate,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** RenameDialog의 현재 이름을 채운 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun RenameDialogPreview(){
    RenameDialog(
        currentName = "report.pdf",
        isDirectory = false,
        onConfirm = {},
        onDismiss = {},
        validate = { emptySet() }
    )
}
