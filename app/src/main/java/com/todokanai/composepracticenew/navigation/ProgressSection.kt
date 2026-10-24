package com.todokanai.composepracticenew.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.todokanai.composepracticenew.compose.dialog.CustomProgressDialog
import com.todokanai.composepracticenew.compose.presets.dialog.OpenDialogRegistry
import com.todokanai.composepracticenew.model.ShownProgress

/**
 * 띄울 작업의 진행률 수집과 CustomProgressDialog 표시를 담당하는 섹션 — 진행률 업데이트 리컴포즈 범위를 이 composable로 한정한다.
 * 다른 다이얼로그가 떠 있는 동안은 그 다이얼로그를 덮지 않도록 표시하지 않고, 모두 닫히면 다시 표시한다.
 * @param onDismiss 다이얼로그를 닫을 때 그 다이얼로그의 작업 인스턴스 ID를 받는 콜백
 * @param onCancel 작업 취소 버튼을 누를 때 그 다이얼로그의 작업 인스턴스 ID를 받는 콜백
 */
@Composable
internal fun ProgressSection(
    shownProgress: StateFlow<ShownProgress?>,
    openDialogRegistry: OpenDialogRegistry,
    onDismiss: (instanceId: Int) -> Unit,
    onCancel: (instanceId: Int) -> Unit
) {
    val shown by shownProgress.collectAsStateWithLifecycle()
    val current = shown

    if (current != null && !openDialogRegistry.hasOpenDialog) {
        CustomProgressDialog(
            progress = current.progress,
            onDismissRequest = { onDismiss(current.instanceId) },
            onCancel = { onCancel(current.instanceId) }
        )
    }
}
