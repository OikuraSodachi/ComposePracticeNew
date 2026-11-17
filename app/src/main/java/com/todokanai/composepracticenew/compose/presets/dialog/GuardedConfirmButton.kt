package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 확인 가능 여부를 [canConfirm] 한 곳으로 정하고, 확인을 한 번만 처리하는 다이얼로그 확인 버튼.
 * 활성 여부(직전 컴포지션의 값)와 클릭 순간의 판정을 같은 [canConfirm]으로 해, 입력을 바꾼 직후 리컴포지션 전에 눌러도 조건을 어긴 확인이 처리되지 않는다.
 * [canConfirm]은 컴포지션 중에도 불리므로 그 안에서 읽은 State가 바뀌면 활성 여부가 따라 바뀐다.
 * 처리한 뒤에는 비활성으로 남아, 다이얼로그가 닫히기 전 연타로 [onConfirm]이 중복 실행되지 않는다 — 다시 확인받으려면 호스트가 새 인스턴스로 연다.
 * @param text 버튼 라벨
 * @param canConfirm 지금 확인할 수 있는지 판정하는 함수
 * @param onConfirm 확인을 처리하는 콜백
 */
@Composable
fun GuardedConfirmButton(
    text: String,
    canConfirm: () -> Boolean,
    onConfirm: () -> Unit,
) {
    // 키 없음 — 버튼이 살아 있는 동안 확인을 한 번만 처리; 상태 쓰기는 리컴포지션 전에도 즉시 보여 연타의 두 번째 클릭을 걸러낸다
    var consumed by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (!consumed && canConfirm()) {
                consumed = true
                onConfirm()
            }
        },
        enabled = !consumed && canConfirm()
    ) {
        Text(text = text)
    }
}
