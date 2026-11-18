package com.todokanai.composepracticenew.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.ui.model.AppNotice
import com.todokanai.composepracticenew.ui.model.appNoticeTextOf

/**
 * 앱 알림(설정 저장소 실패, 파일 작업 메시지, 원격 끊김·연결 실패)을 어느 화면에 있든 화면 하단 Snackbar로 하나씩 알린다. 화면 위에 겹쳐 두며, 터치는 아래 화면으로 그대로 간다.
 * @param showNotices 알림을 하나씩 받아 주어진 함수로 보여 주고, 취소될 때까지 돌아오지 않는 함수([com.todokanai.composepracticenew.viewmodel.AppNoticeViewModel.showNotices])
 */
@Composable
fun AppNoticeHost(showNotices: suspend (suspend (AppNotice) -> Unit) -> Nothing) {
    val context = LocalContext.current
    // 키 없음 — 호스트가 살아 있는 동안 하나만 둔다. 모든 알림이 한 Snackbar 대기열을 함께 써 한 번에 하나만 보이도록
    val hostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    // lifecycleOwner 키 — 소유자가 바뀌면 새 소유자의 수명으로 다시 시작.
    // 화면이 보이는 동안만 꺼내, 보이지 않는 Activity가 알림을 가져가지 않고 보이지 않는 동안 생긴 알림은 다시 보일 때 알리도록.
    // Toast가 아니라 Snackbar로 알린다 — showSnackbar는 사라질 때까지 돌아오지 않아, 다 보여 준 알림만 지우고 시스템 Toast 한도로 버려지는 알림이 없도록
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            showNotices { notice ->
                hostState.showSnackbar(appNoticeTextOf(notice).resolve(context), duration = snackbarDurationOf(notice))
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(hostState = hostState)
    }
}

/**
 * 알림 [notice]를 보여 줄 시간을 정한다. 설정 실패와 작업 실패는 원인을 읽을 수 있게 길게, 작업 완료와 원격 끊김·연결 실패는 확인만 하면 되므로 짧게 보인다.
 * @param notice 보여 줄 알림
 * @return Snackbar를 보여 줄 시간
 */
private fun snackbarDurationOf(notice: AppNotice): SnackbarDuration = when (notice) {
    is AppNotice.PreferenceFailed -> SnackbarDuration.Long
    is AppNotice.Operation -> when (notice.notice.kind) {
        OperationNoticeKind.FAILURE -> SnackbarDuration.Long
        OperationNoticeKind.COMPLETION -> SnackbarDuration.Short
    }
    AppNotice.ConnectionLost, is AppNotice.ConnectFailed -> SnackbarDuration.Short
}
