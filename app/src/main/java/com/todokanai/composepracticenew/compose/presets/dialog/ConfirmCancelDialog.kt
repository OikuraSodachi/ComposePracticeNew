package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R

/**
 * 확인·취소 버튼을 갖는 다이얼로그의 공통 골격.
 *
 * 확인 버튼은 [onConfirm] 실행 후 [onDismiss]로 다이얼로그를 닫고, 취소 버튼·바깥 터치·뒤로가기는 [onDismiss]만 호출한다.
 * 버튼 라벨은 항상 값을 가지므로 라벨 없는 버튼은 만들어지지 않는다.
 * [onConfirm]이 null이면 확인 버튼을 표시하지 않고, [title]이 null이면 제목 영역 자체를 두지 않는다.
 * 확인 버튼은 [GuardedConfirmButton]이다 — 확인은 다이얼로그 인스턴스당 한 번만 처리되고, 활성 여부와 클릭 순간의 판정을 모두 [canConfirm]으로 한다.
 * 입력을 검증하는 다이얼로그는 그 조건을 [canConfirm]에 담는다. false면 확인을 처리하지 않고 열어 둔다.
 * [content]는 남는 높이를 넘으면 세로로 스크롤된다. 스스로 세로 스크롤하는 본문(LazyColumn 등)이면 [contentScrollsItself]를 true로 준다.
 */
@Composable
fun ConfirmCancelDialog(
    modifier: Modifier,
    title: String?,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    canConfirm: () -> Boolean = { true },
    confirmText: String = stringResource(R.string.btn_confirm),
    dismissText: String = stringResource(R.string.btn_cancel),
    contentScrollsItself: Boolean = false,
    content: @Composable () -> Unit
) {
    AppAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = title?.let { t -> @Composable { Text(text = t) } },
        text = content,
        textScrollsItself = contentScrollsItself,
        confirmButton = {
            onConfirm?.let {
                GuardedConfirmButton(
                    text = confirmText,
                    canConfirm = canConfirm,
                    onConfirm = {
                        it()
                        onDismiss()
                    }
                )
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}

/** ConfirmCancelDialog 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun ConfirmCancelDialogPreview() {
    ConfirmCancelDialog(
        modifier = Modifier,
        title = "title",
        onConfirm = {},
        onDismiss = {}
    ) {
        Text("content")
    }
}
