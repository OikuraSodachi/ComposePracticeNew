package com.todokanai.composepracticenew.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.ConfirmMode
import com.todokanai.composepracticenew.ui.model.SelectMode

/** 확인 모드에서 취소 버튼과, 모드가 보유한 라벨을 표시하는 확인 버튼을 제공한다. */
@Composable
fun ConfirmButtons(
    modifier: Modifier,
    confirm:()->Unit,
    cancel:()->Unit,
    mode: ConfirmMode
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        TextButton(
            onClick = cancel,
            Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.btn_cancel))
        }
        TextButton(
            onClick = confirm,
            Modifier.weight(1f)
        ) {
            Text(stringResource(mode.labelRes))
        }
    }
}

/**
 * 확인을 다른 화면에서 하는 모드에서 확인 버튼 대신 할 일을 알리는 문구와 취소 버튼을 보인다. 높이는 [ConfirmButtons]와 같다 — 모드가 바뀌어도 목록 둘레가 움직이지 않도록.
 * @param modifier 레이아웃 수정자
 * @param hintRes 할 일을 알리는 문자열 리소스
 * @param cancel 모드를 끝내는 콜백
 */
@Composable
fun HintCancelButtons(
    modifier: Modifier,
    @StringRes hintRes: Int,
    cancel: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = cancel,
            Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.btn_cancel))
        }
        Text(
            text = stringResource(hintRes),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/** ConfirmButtons 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun ConfirmButtonsPreview(){
    Surface() {
        ConfirmButtons(
            modifier = Modifier,
            confirm = {},
            cancel = {},
            mode = SelectMode.LocalOperation.Unzip
        )
    }
}
