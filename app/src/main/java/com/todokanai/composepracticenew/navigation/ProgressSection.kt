package com.todokanai.composepracticenew.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.todokanai.composepracticenew.compose.dialog.CustomProgressDialog
import com.todokanai.composepracticenew.model.ProgressStateModel

/** progressMap 수집과 CustomProgressDialog 표시를 담당하는 섹션 — 진행률 업데이트 리컴포즈 범위를 이 composable로 한정한다. */
@Composable
internal fun ProgressSection(
    progressMap: StateFlow<Map<Int, ProgressStateModel>>,
    forceShowTrigger: StateFlow<Boolean>,
    onDialogShown: () -> Unit,
    onCancel: () -> Unit
) {
    val progressMap by progressMap.collectAsStateWithLifecycle()
    // 닫을 당시 진행 중이던 작업 ID — 이 목록에 없는 새 작업이 시작되면 다이얼로그를 다시 표시한다
    var dismissedIds by remember { mutableStateOf(emptySet<Int>()) }
    val forceShow by forceShowTrigger.collectAsStateWithLifecycle()

    // forceShow를 키로 사용 — 알림 클릭으로 true가 될 때만 다이얼로그를 강제 표시한다
    LaunchedEffect(forceShow) {
        if (forceShow) {
            dismissedIds = emptySet()
            onDialogShown()
        }
    }

    val activeProgressMap = progressMap.filter { (_, state) -> state.progress < 100 }
    val showProgress = activeProgressMap.keys.any { it !in dismissedIds }

    if (showProgress) {
        CustomProgressDialog(
            progressMap = activeProgressMap,
            onDismissRequest = { dismissedIds = activeProgressMap.keys },
            onCancel = onCancel
        )
    }
}
