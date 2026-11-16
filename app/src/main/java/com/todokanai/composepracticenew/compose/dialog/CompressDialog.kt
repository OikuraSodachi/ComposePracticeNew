package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileNameError

/**
 * 압축 파일 이름(확장자 제외)과 선택적으로 비밀번호를 입력받는 다이얼로그. 이름 규칙은 [validate]로 주입받는다.
 * 비밀번호 설정을 켜면 비밀번호와 그 확인을 입력받으며, 둘이 같고 비어 있지 않아야 확인할 수 있다.
 * @param initialText 입력란에 미리 채울 기본 압축 파일 이름(확장자 제외)
 * @param onConfirm 확인 시 입력 이름과 비밀번호(설정하지 않았으면 null)를 전달받는 콜백
 * @param onDismiss 다이얼로그를 닫는 콜백
 * @param validate 입력 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 */
@Composable
fun CompressDialog(
    initialText: String,
    onConfirm: (name: String, password: String?) -> Unit,
    onDismiss: () -> Unit,
    validate: (String) -> Set<FileNameError>
){
    // 키 없음(호스트가 연 회차로 key) — 회전에도 유지한다: 회전 뒤 꺼진 줄 모르고 확인하면 비밀번호 없는 압축 파일이 만들어지므로
    var protect by rememberSaveable { mutableStateOf(false) }
    // 키 없음 — 비밀번호를 저장 상태(Bundle)에 남기지 않도록 remember만 쓴다. 회전하면 비워지며, 빈 비밀번호로는 확인할 수 없어 다시 입력하게 된다
    var password by remember { mutableStateOf("") }
    // 위와 같은 이유로 remember만 쓴다
    var confirmation by remember { mutableStateOf("") }

    FileNameDialog(
        title = stringResource(R.string.dialog_zip_title),
        hint = stringResource(R.string.dialog_zip_hint),
        initialText = initialText,
        validate = validate,
        onConfirm = { name -> onConfirm(name, password.takeIf { protect }) },
        onDismiss = onDismiss,
        extraValid = { !protect || isPasswordReady(password, confirmation) }
    ) {
        PasswordSettingFields(
            protect = protect,
            onProtectChange = { protect = it },
            password = password,
            onPasswordChange = { password = it },
            confirmation = confirmation,
            onConfirmationChange = { confirmation = it }
        )
    }
}

/**
 * 비밀번호 설정 체크박스와, 켰을 때 보이는 비밀번호·비밀번호 확인 입력란.
 * 확인 입력란은 입력이 있고 비밀번호와 다를 때만 불일치를 표시한다 — 입력하기 전부터 오류가 보이지 않도록.
 * @param protect 비밀번호 설정을 켰는지
 * @param onProtectChange 체크 상태가 바뀔 때의 콜백
 * @param password 비밀번호 입력값
 * @param onPasswordChange 비밀번호 입력이 바뀔 때의 콜백
 * @param confirmation 비밀번호 확인 입력값
 * @param onConfirmationChange 비밀번호 확인 입력이 바뀔 때의 콜백
 */
@Composable
private fun PasswordSettingFields(
    protect: Boolean,
    onProtectChange: (Boolean) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = protect, onCheckedChange = onProtectChange)
            Text(stringResource(R.string.dialog_zip_password_protect))
        }
        if (protect) {
            PasswordField(
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.dialog_password_label)
            )
            PasswordField(
                value = confirmation,
                onValueChange = onConfirmationChange,
                label = stringResource(R.string.dialog_password_confirm_label),
                error = if (confirmation.isNotEmpty() && confirmation != password) stringResource(R.string.dialog_password_mismatch) else null
            )
        }
    }
}

/**
 * 비밀번호 설정을 켠 상태에서 확인할 수 있는 입력인지 판정한다.
 * @param password 비밀번호 입력값
 * @param confirmation 비밀번호 확인 입력값
 * @return 비밀번호가 비어 있지 않고 확인과 같으면 true
 */
private fun isPasswordReady(password: String, confirmation: String): Boolean =
    password.isNotEmpty() && password == confirmation

/** CompressDialog의 기본 이름을 채운 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun CompressDialogPreview(){
    CompressDialog(
        initialText = "Download",
        onConfirm = { _, _ -> },
        onDismiss = {},
        validate = { emptySet() }
    )
}
