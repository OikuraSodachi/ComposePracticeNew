package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileNameParts

/**
 * 파일·폴더 이름을 입력받는 다이얼로그. 새 폴더·이름 변경·압축 파일명이 공통으로 사용한다.
 *
 * 이름 규칙은 [validate]로 주입받고, 위반 사유는 [fileNameErrorMessage]로 표시 문구를 만들어 입력란 아래에 보여준다.
 * 열면 [initialText]를 선택해 두어, 바로 타이핑하면 기본값이 바뀐다.
 * @param title 다이얼로그 제목
 * @param hint 입력란이 비어 있을 때 보여줄 안내 문구
 * @param initialText 입력란의 초기값(예: 이름 변경 시 현재 이름)
 * @param selectBaseNameOnly true면 [initialText]의 확장자 앞까지만 선택한다 — 파일 이름을 바꿀 때 확장자를 지키도록
 * @param validate 입력 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 * @param onConfirm 확인 시 입력 이름을 전달받는 콜백
 * @param onDismiss 다이얼로그를 닫는 콜백
 */
@Composable
fun FileNameDialog(
    title: String,
    hint: String,
    initialText: String = "",
    selectBaseNameOnly: Boolean = false,
    validate: (String) -> Set<FileNameError>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    EditTextDialog(
        modifier = Modifier,
        title = title,
        defaultText = hint,
        initialText = initialText,
        initialSelection = initialSelection(initialText, selectBaseNameOnly),
        validate = validate,
        errorMessage = { fileNameErrorMessage(it) },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 다이얼로그를 열 때 선택해 둘 범위를 정한다.
 * @param initialText 입력란의 초기값
 * @param selectBaseNameOnly true면 확장자 앞까지만 선택
 * @return 선택 범위
 */
private fun initialSelection(initialText: String, selectBaseNameOnly: Boolean): TextRange =
    TextRange(0, if (selectBaseNameOnly) FileNameParts.baseNameEnd(initialText) else initialText.length)

/**
 * [FileNameError]를 입력란 아래에 표시할 사유 문구로 바꾼다. 새 사유가 추가되면 when이 누락을 컴파일 단계에서 드러낸다.
 * @param error 표시할 위반 사유
 * @return 사용자에게 보여줄 문구
 */
@Composable
private fun fileNameErrorMessage(error: FileNameError): String = when (error) {
    FileNameError.BLANK -> stringResource(R.string.error_file_name_blank)
    FileNameError.LEADING_OR_TRAILING_WHITESPACE -> stringResource(R.string.error_file_name_edge_whitespace)
    FileNameError.CONTAINS_PATH_SEPARATOR -> stringResource(R.string.error_file_name_path_separator)
    FileNameError.CONTAINS_CONTROL_CHAR -> stringResource(R.string.error_file_name_control_char)
    FileNameError.RESERVED_NAME -> stringResource(R.string.error_file_name_reserved)
    FileNameError.TOO_LONG -> stringResource(R.string.error_file_name_too_long)
    FileNameError.EXISTS_AS_OTHER_TYPE -> stringResource(R.string.error_file_name_other_type)
}

/** FileNameDialog의 빈 입력 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun FileNameDialogPreview() {
    FileNameDialog(
        title = "title",
        hint = "name",
        validate = { emptySet() },
        onConfirm = {},
        onDismiss = {}
    )
}
