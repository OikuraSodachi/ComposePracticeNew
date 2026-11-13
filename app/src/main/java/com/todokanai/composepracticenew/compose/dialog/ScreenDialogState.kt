package com.todokanai.composepracticenew.compose.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random

/**
 * 화면(back stack 항목) 하나가 여는 다이얼로그의 열림 상태. 화면 단위 ViewModel에 보관해, 다이얼로그의 대상(ViewModel의 선택 등)과 수명을 맞춘다.
 * 다이얼로그를 열 때마다 새 [openId]를 발급해, 이전에 열었던 다이얼로그의 저장된 입력값이 새로 연 다이얼로그에 복원되지 않게 한다.
 */
abstract class ScreenDialogState {
    /** 마지막으로 다이얼로그를 연 회차의 식별자. 다이얼로그를 key로 감싸 회차별로 입력 상태를 분리하는 데 쓴다. */
    var openId by mutableStateOf(0L); private set

    /** 다이얼로그를 여는 함수가 열기 전에 불러 새 회차를 발급한다. 프로세스가 다시 시작돼도 이전 회차와 겹치지 않도록 무작위 값을 쓴다. */
    protected fun beginOpening() { openId = Random.nextLong() }

    /** 열린 다이얼로그를 모두 닫는다. */
    abstract fun dismissAll()
}

/**
 * 화면 단위 ViewModel에 보관된 [state]를 이 화면의 다이얼로그 상태로 쓴다.
 * 화면 회전 등 구성 변경에는 열린 다이얼로그를 유지하고, 다른 화면으로 이동하거나 프로세스가 종료된 뒤 돌아오면 닫힌 상태로 시작한다.
 */
@Composable
fun <T : ScreenDialogState> rememberScreenDialogState(state: T): T {
    val activity = LocalContext.current.findActivity()
    // state를 키로 — 같은 화면의 상태 인스턴스가 유지되는 동안 한 번만 등록하고, 화면이 컴포지션을 떠날 때
    // 구성 변경이 아니면(다른 화면으로 이동 등) 다이얼로그를 닫아 돌아왔을 때 다시 열리지 않게 한다
    DisposableEffect(state) {
        onDispose {
            if (activity?.isChangingConfigurations != true) state.dismissAll()
        }
    }
    return state
}

/** [Context]를 감싼 [ContextWrapper]를 따라가 [Activity]를 찾는다. 없으면 null. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
