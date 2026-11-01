package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 진행률 다이얼로그를 뺀 다이얼로그가 지금 몇 개 떠 있는지 세는 레지스트리.
 * 다이얼로그는 각자 별도 창이라 나중에 뜬 쪽이 위를 덮으므로, 진행률 다이얼로그는 이 레지스트리가 비어 있을 때만 표시해 다른 다이얼로그를 덮지 않는다.
 */
@Stable
class OpenDialogRegistry {

    /** 지금 떠 있는, 진행률 다이얼로그를 뺀 다이얼로그 수. */
    private var openCount by mutableStateOf(0)

    /** 진행률 다이얼로그를 뺀 다이얼로그가 하나라도 떠 있는지. */
    val hasOpenDialog: Boolean get() = openCount > 0

    internal fun open() {
        openCount++
    }

    internal fun close() {
        openCount--
    }
}

/** 진행률 다이얼로그와 같은 화면 트리의 다이얼로그가 등록하는 [OpenDialogRegistry]. 제공되지 않은 곳(Preview 등)에서는 null이라 등록하지 않는다. */
val LocalOpenDialogRegistry = staticCompositionLocalOf<OpenDialogRegistry?> { null }

/**
 * 진행률 다이얼로그를 뺀 앱의 모든 다이얼로그가 쓰는 [AlertDialog].
 * 떠 있는 동안 [LocalOpenDialogRegistry]에 등록되어, 진행률 다이얼로그가 이 다이얼로그를 덮지 않고 비켜난다.
 * 본문은 남는 높이를 넘으면 세로로 스크롤된다 — [AlertDialog]는 본문을 남는 높이로 줄이기만 하므로, 감싸지 않으면 넘치는 부분이 표시 없이 잘린다.
 * @param onDismissRequest 바깥 탭·뒤로가기로 닫을 때의 콜백
 * @param confirmButton 확인 버튼 영역
 * @param modifier 다이얼로그에 적용할 Modifier
 * @param dismissButton 취소 버튼 영역. null이면 두지 않는다
 * @param title 제목 영역. null이면 두지 않는다
 * @param text 본문 영역. null이면 두지 않는다
 * @param textScrollsItself 본문이 스스로 세로 스크롤하면(LazyColumn 등) true — 세로 스크롤 안에 세로 스크롤을 넣으면 측정에 실패하므로 감싸지 않는다
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    textScrollsItself: Boolean = false
) {
    RegisterOpenDialog()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = if (text == null || textScrollsItself) text else { { ScrollableText(text) } }
    )
}

/** 다이얼로그 본문 [content]를 세로 스크롤 영역에 둔다. [AlertDialog]의 본문 슬롯처럼 Box로 감싸 본문의 배치는 그대로다. */
@Composable
private fun ScrollableText(content: @Composable () -> Unit) {
    // 키 없음 — 다이얼로그가 떠 있는 동안 스크롤 위치를 유지하고, 회전 후에도 보던 위치로 돌아오도록 saveable 상태를 쓴다
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.verticalScroll(scrollState)) {
        content()
    }
}

/** 이 composable이 composition에 있는 동안 다이얼로그가 떠 있다고 [LocalOpenDialogRegistry]에 알린다. */
@Composable
private fun RegisterOpenDialog() {
    val registry = LocalOpenDialogRegistry.current ?: return
    // registry 키 — 다이얼로그가 composition에 들어오면 세고, 나가거나 registry가 바뀌면 센 것을 되돌려 수가 어긋나지 않게 한다
    DisposableEffect(registry) {
        registry.open()
        onDispose { registry.close() }
    }
}
