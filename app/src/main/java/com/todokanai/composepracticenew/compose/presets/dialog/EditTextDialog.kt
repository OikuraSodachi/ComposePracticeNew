package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R

/**
 * 한 줄 텍스트를 입력받아 확인 시 전달하는 다이얼로그.
 *
 * 검증 규칙은 [validate]로, 오류 사유의 표시 문구는 [errorMessage]로 주입받는다. 이 다이얼로그는 결과에 따라
 * 확인 버튼 활성 여부와 입력란 오류 표시만 담당하며, 규칙 자체는 알지 못한다.
 * 오류는 사용자가 입력을 한 번이라도 바꾼 뒤에만 표시해, 빈 입력으로 열자마자 오류가 보이지 않게 한다.
 *
 * @param E 검증 오류 사유의 타입
 */
@Composable
fun <E> EditTextDialog(
    modifier: Modifier,
    title: String,
    defaultText:String,
    initialText: String = "",
    dismissText: String = stringResource(R.string.btn_cancel),
    confirmText: String = stringResource(R.string.btn_confirm),
    validate: (String) -> Set<E>,
    errorMessage: @Composable (E) -> String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 키 없음 — 다이얼로그가 떠 있는 동안 사용자 입력을 유지하고, initialText가 바뀌어도 입력 중인 값을 덮어쓰지 않음
    var text by remember { mutableStateOf(initialText) }
    // 키 없음 — 구성 변경 후에도 이미 보이던 오류 표시가 사라지지 않도록 편집 여부를 보존
    var edited by rememberSaveable { mutableStateOf(false) }
    // rememberUpdatedState — 아래 derivedStateOf가 재생성 없이 항상 최신 validate를 호출하도록 보장
    val currentValidate by rememberUpdatedState(validate)
    // text·currentValidate를 추적 — 입력이 바뀔 때만 재검증하고, 결과 집합이 같으면 본문을 무효화하지 않음
    val errors by remember { derivedStateOf { currentValidate(text) } }
    val shownError = if (edited) errors.firstOrNull() else null

    ConfirmCancelDialog(
        modifier = modifier,
        title = title,
        onConfirm = { onConfirm(text) },
        onDismiss = onDismiss,
        confirmEnabled = errors.isEmpty(),
        confirmText = confirmText,
        dismissText = dismissText
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            placeholder = {Text(defaultText)},
            onValueChange = {
                text = it
                edited = true
            },
            singleLine = true,
            isError = shownError != null,
            supportingText = shownError?.let { error -> @Composable { Text(errorMessage(error)) } }
        )
    }
}

/** 공백만 아니면 통과하는 검증으로 EditTextDialog 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun EditTextDialogPreview(){
    EditTextDialog(
        modifier = Modifier,
        title = "title",
        defaultText = "default text",
        validate = { if (it.isBlank()) setOf("blank") else emptySet() },
        errorMessage = { it },
        onConfirm = {},
        onDismiss = {}
    )
}
