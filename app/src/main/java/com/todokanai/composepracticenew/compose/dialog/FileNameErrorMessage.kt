package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileNameError

/** [FileNameError]를 입력란 아래에 표시할 사유 문구로 바꾼다. 새 사유가 추가되면 when이 누락을 컴파일 단계에서 드러낸다. */
@Composable
fun fileNameErrorMessage(error: FileNameError): String = when (error) {
    FileNameError.BLANK -> stringResource(R.string.error_file_name_blank)
    FileNameError.LEADING_OR_TRAILING_WHITESPACE -> stringResource(R.string.error_file_name_edge_whitespace)
    FileNameError.CONTAINS_PATH_SEPARATOR -> stringResource(R.string.error_file_name_path_separator)
    FileNameError.CONTAINS_CONTROL_CHAR -> stringResource(R.string.error_file_name_control_char)
    FileNameError.RESERVED_NAME -> stringResource(R.string.error_file_name_reserved)
    FileNameError.TOO_LONG -> stringResource(R.string.error_file_name_too_long)
}
