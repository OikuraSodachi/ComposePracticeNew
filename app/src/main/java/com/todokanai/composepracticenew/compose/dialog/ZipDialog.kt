package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileNameError

/**
 * 압축 파일 이름(확장자 제외)을 입력받는 다이얼로그. 이름 규칙은 [validate]로 주입받는다.
 * @param initialText 입력란에 미리 채울 기본 압축 파일 이름(확장자 제외)
 * @param onConfirm 확인 시 입력 이름을 전달받는 콜백
 * @param onDismiss 다이얼로그를 닫는 콜백
 * @param validate 입력 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 */
@Composable
fun ZipDialog(
    initialText: String,
    onConfirm: (text:String)->Unit,
    onDismiss: ()-> Unit,
    validate: (String) -> Set<FileNameError>
){
    FileNameDialog(
        title = stringResource(R.string.dialog_zip_title),
        hint = stringResource(R.string.dialog_zip_hint),
        initialText = initialText,
        validate = validate,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** ZipDialog의 기본 이름을 채운 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun ZipDialogPreview(){
    ZipDialog(
        initialText = "Download",
        onConfirm = {},
        onDismiss = {},
        validate = { emptySet() }
    )
}
