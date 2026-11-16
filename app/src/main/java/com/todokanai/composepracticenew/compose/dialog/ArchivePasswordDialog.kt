package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog

/**
 * 압축 해제 중 암호화된 압축 파일을 만났을 때 비밀번호를 묻는 다이얼로그.
 * 바깥을 눌러 닫지 않는다 — 답하지 않고 닫으면 작업이 계속 기다리므로. 질문마다 새 입력으로 시작하도록 호스트가 질문으로 key를 건다.
 * @param archiveName 비밀번호를 묻는 압축 파일의 표시 이름
 * @param wrongPassword 앞서 입력한 비밀번호가 틀려 다시 묻는 것이면 true
 * @param onConfirm 입력한 비밀번호를 전달하는 콜백
 * @param onCancel 압축 해제 작업을 취소하는 콜백
 */
@Composable
fun ArchivePasswordDialog(
    archiveName: String,
    wrongPassword: Boolean,
    onConfirm: (password: String) -> Unit,
    onCancel: () -> Unit,
) {
    // 키 없음(호스트가 질문으로 key) — 비밀번호를 저장 상태(Bundle)에 남기지 않도록 rememberSaveable 대신 remember를 쓴다. 회전하면 비워지며 다시 입력한다
    var password by remember { mutableStateOf("") }
    // 다이얼로그가 떠 있는 동안 같은 요청자를 유지 — 리컴포지션마다 새로 만들면 포커스 요청이 연결되지 않은 요청자로 간다
    val focusRequester = remember { FocusRequester() }

    AppAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.dialog_archive_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_archive_password_message, archiveName))
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    modifier = Modifier.focusRequester(focusRequester),
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.dialog_password_label),
                    error = if (wrongPassword) stringResource(R.string.dialog_archive_password_wrong) else null
                )
                // 열릴 때 한 번 포커스를 준다 — AlertDialog는 내용을 별도 창에서 구성하므로, 입력란과 같은 구성 안에 두어야 요청 시점에 요청자가 연결돼 있다
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

/** ArchivePasswordDialog의 틀린 비밀번호로 다시 묻는 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun ArchivePasswordDialogPreview() {
    ArchivePasswordDialog(
        archiveName = "photos.zip",
        wrongPassword = true,
        onConfirm = {},
        onCancel = {}
    )
}
