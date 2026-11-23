package com.todokanai.composepracticenew.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * 처리를 기다리는 요청([com.todokanai.composepracticenew.tools.PendingRequest])을 화면이 [minActiveState] 이상인 동안 받아 처리하고, 처리한 직후 처리했다고 알린다(#534).
 * 처리([handle])와 알림([onHandled]) 사이에 멈추지 않으므로, 그 사이에 취소되어 같은 요청을 두 번 처리하지 않는다.
 * 처리하기 전에 취소되면 요청이 남아 있어 다음에 [minActiveState]가 되면 처리한다.
 * @param request 처리를 기다리는 요청. 없으면 null
 * @param minActiveState 요청을 처리할 최소 수명 상태. RESUMED면 화면이 입력을 받을 때까지 요청을 보관한다
 * @param onHandled 처리한 요청을 알리는 함수
 * @param handle 요청을 처리하는 함수
 */
@Composable
fun <T : Any> PendingRequestEffect(
    request: StateFlow<T?>,
    minActiveState: Lifecycle.State,
    onHandled: (T) -> Unit,
    handle: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // 최신 람다로 처리 — 아래 효과를 다시 시작하지 않고도 리컴포지션이 넘긴 새 람다(새로 캡처한 값)를 쓰도록
    val currentHandle by rememberUpdatedState(handle)
    val currentOnHandled by rememberUpdatedState(onHandled)
    // 요청 원본·수명 소유자·최소 상태를 키로 — 하나라도 바뀌면 이전 수집을 끝내고 새 값으로 다시 기다린다
    LaunchedEffect(request, lifecycleOwner, minActiveState) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            request.filterNotNull().collect { pending ->
                currentHandle(pending)
                currentOnHandled(pending)
            }
        }
    }
}
