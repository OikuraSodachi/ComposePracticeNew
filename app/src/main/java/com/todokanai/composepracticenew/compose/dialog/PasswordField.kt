package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview

/**
 * 압축 파일 비밀번호를 가려서 입력받는 한 줄 입력란. 압축(비밀번호 설정)과 압축 해제(비밀번호 질문)가 함께 쓴다.
 * @param value 입력란의 현재 값
 * @param onValueChange 입력이 바뀔 때 새 값을 전달받는 콜백
 * @param label 입력란의 이름
 * @param modifier 입력란에 적용할 Modifier
 * @param error 입력란 아래에 보여 줄 오류 문구. null이면 오류를 표시하지 않는다
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    TextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error != null,
        supportingText = error?.let { message -> @Composable { Text(message) } }
    )
}

/** PasswordField의 오류 표시 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun PasswordFieldPreview() {
    PasswordField(
        value = "secret",
        onValueChange = {},
        label = "Password",
        error = "error"
    )
}
