package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R

/**
 * 한 줄 텍스트를 입력받아 확인 시 전달하는 다이얼로그.
 *
 * 검증 규칙은 [validate]로, 오류 사유의 표시 문구는 [errorMessage]로 주입받는다. 이 다이얼로그는 결과에 따라
 * 확인 버튼 활성 여부와 입력란 오류 표시만 담당하며, 규칙 자체는 알지 못한다.
 * 빈 입력으로 열면 오류는 사용자가 입력을 한 번이라도 바꾼 뒤에만 표시해, 열자마자 오류가 보이지 않게 한다.
 * [initialText]로 미리 채워 열면 처음부터 표시해, 미리 채운 값이 규칙을 어길 때 확인 버튼이 이유 없이 비활성으로 보이지 않게 한다.
 * 열면 입력란에 포커스를 주고 [initialSelection]을 선택해 두어, 바로 타이핑하면 선택한 부분이 바뀐다.
 *
 * @param E 검증 오류 사유의 타입
 */
@Composable
fun <E> EditTextDialog(
    modifier: Modifier,
    title: String,
    defaultText:String,
    initialText: String = "",
    initialSelection: TextRange = TextRange(0, initialText.length),
    dismissText: String = stringResource(R.string.btn_cancel),
    confirmText: String = stringResource(R.string.btn_confirm),
    validate: (String) -> Set<E>,
    errorMessage: @Composable (E) -> String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 입력값(선택 범위 포함)과 편집 여부는 같은 rememberSaveable로 함께 보존 — 키 없음: initialText가 바뀌어도 입력 중인 값을 덮어쓰지 않음.
    // 호스트가 표시 상태를 저장하면 구성 변경 후 입력값과 오류 표시가 짝을 이뤄 복원되고(한쪽만 복원되어 빈 입력에 오류가 뜨는 일 방지),
    // 호스트 표시 상태가 remember라 다이얼로그가 닫히는 경우에는 함께 버려진다.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, initialSelection))
    }
    // 미리 채운 값이 있으면 처음부터 편집된 것으로 보아 오류를 바로 표시한다
    var edited by rememberSaveable { mutableStateOf(initialText.isNotEmpty()) }
    // rememberUpdatedState — 아래 derivedStateOf가 재생성 없이 항상 최신 validate를 호출하도록 보장
    val currentValidate by rememberUpdatedState(validate)
    // field·currentValidate를 추적 — 입력 문자열이 바뀔 때만 재검증하고(커서 이동은 무시), 결과 집합이 같으면 본문을 무효화하지 않음
    val errors by remember { derivedStateOf { currentValidate(field.text) } }
    val shownError = if (edited) errors.firstOrNull() else null
    // 다이얼로그가 떠 있는 동안 같은 요청자를 유지 — 리컴포지션마다 새로 만들면 포커스 요청이 연결되지 않은 요청자로 간다
    val focusRequester = remember { FocusRequester() }

    ConfirmCancelDialog(
        modifier = modifier,
        title = title,
        onConfirm = { onConfirm(field.text) },
        onDismiss = onDismiss,
        confirmEnabled = errors.isEmpty(),
        confirmText = confirmText,
        dismissText = dismissText
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = field,
            placeholder = {Text(defaultText)},
            onValueChange = {
                if (it.text != field.text) edited = true
                field = it
            },
            singleLine = true,
            isError = shownError != null,
            supportingText = shownError?.let { error -> @Composable { Text(errorMessage(error)) } }
        )
        // 열릴 때 한 번 포커스를 준다 — 포커스가 없으면 미리 선택한 범위가 보이지 않고, 입력란을 누르는 순간 선택이 풀린다.
        // AlertDialog는 내용을 별도 창에서 구성하므로, 입력란과 같은 구성 안에 두어야 요청 시점에 요청자가 연결돼 있다
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
