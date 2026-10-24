package com.todokanai.composepracticenew.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.todokanai.composepracticenew.compose.dialog.CustomProgressDialog
import com.todokanai.composepracticenew.model.ProgressStateModel

/** 표시할 진행률 수집과 CustomProgressDialog 표시를 담당하는 섹션 — 진행률 업데이트 리컴포즈 범위를 이 composable로 한정한다. */
@Composable
internal fun ProgressSection(
    visibleProgress: StateFlow<Map<Int, ProgressStateModel>>,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val visibleProgress by visibleProgress.collectAsStateWithLifecycle()

    if (visibleProgress.isNotEmpty()) {
        CustomProgressDialog(
            progressMap = visibleProgress,
            onDismissRequest = onDismiss,
            onCancel = onCancel
        )
    }
}
