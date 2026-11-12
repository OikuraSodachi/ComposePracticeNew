package com.todokanai.composepracticenew.compose.dialog

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog
import kotlinx.coroutines.delay

/** 확인이 이 시간보다 오래 걸릴 때만 표시한다 — 대부분 금방 끝나는 확인에 창이 깜박이지 않도록(ContentLoadingProgressBar와 같은 값). */
private const val SHOW_DELAY_MILLIS = 500L

/** 한번 표시하면 이 시간은 유지한다 — 표시 직후 확인이 끝나 창이 깜박이지 않도록. */
private const val MIN_SHOW_MILLIS = 500L

/**
 * 업로드 전 겹침 확인이 오래 걸릴 때 진행 중임을 알리고 멈출 수 있게 하는 다이얼로그. 확인이 [SHOW_DELAY_MILLIS]를 넘길 때만 보이고, 보이면 [MIN_SHOW_MILLIS]는 유지한다.
 * @param checking 업로드 전 확인이 진행 중인지
 * @param onCancel 사용자가 확인을 멈출 때 실행할 콜백
 */
@Composable
fun UploadCheckingDialog(checking: Boolean, onCancel: () -> Unit) {
    // 키 없이 보관 — checking이 바뀌어도 표시 여부와 표시 시작 시각을 이어서 판단해야 한다
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf(0L) }

    // checking이 바뀔 때마다 타이머를 다시 건다 — 켜지면 지연 뒤에 보이고, 꺼지면 최소 표시 시간을 채운 뒤 숨긴다
    LaunchedEffect(checking) {
        if (checking) {
            if (!visible) {
                delay(SHOW_DELAY_MILLIS)
                visible = true
                shownAt = SystemClock.uptimeMillis()
            }
        } else if (visible) {
            delay(MIN_SHOW_MILLIS - (SystemClock.uptimeMillis() - shownAt))
            visible = false
        }
    }

    if (!visible) return
    AppAlertDialog(
        onDismissRequest = onCancel,
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.dialog_upload_checking_message))
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
