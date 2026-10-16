package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode

/**
 * 로컬 화면의 하단 파일 작업 버튼(ModeAwareButtons)과 관련 다이얼로그(BottomButtonDialogHost)를 조합한다.
 * 충돌 검사와 실행 여부 판단은 ViewModel이 수행하며, 이 컴포저블은 결과에 따라 선택 상태만 초기화한다.
 */
@Composable
fun BottomButtonListView(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    onSelectModeChange: (LocalSelectMode) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: List<FileHolderItem>,
    pendingConflict: PendingConflict?,
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    onRequestConfirm: (List<FileHolderItem>, SelectMode.LocalOperation) -> Boolean,
    onResolveConflict: (ConflictResolution) -> Boolean,
    onZip: (List<FileHolderItem>, String) -> Unit,
    onRename: (FileHolderItem, String) -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
) {
    val dialogState = rememberBottomButtonDialogState()

    // rememberUpdatedState — 람다를 재생성하지 않고 실행 시점에 최신 선택 목록을 읽도록 보장
    val latestSelectedList by rememberUpdatedState(selectedList)

    // 키 없음 — onSelectModeChange·onClearSelection은 ViewModel 메서드 참조라 안정적이므로 재생성 불필요
    val onResetSelection: () -> Unit = remember {
        {
            onSelectModeChange(SelectMode.Default)
            onClearSelection()
        }
    }

    // 키 없음 — 선택 목록은 rememberUpdatedState로 실행 시점에 읽고, 나머지 캡처는 안정 참조
    val onConfirmWithConflictCheck: (SelectMode.LocalOperation) -> Unit = remember {
        { mode -> if (onRequestConfirm(latestSelectedList, mode)) onResetSelection() }
    }

    ModeAwareButtons(
        modifier = modifier,
        selectMode = selectMode,
        selectedList = selectedList,
        dialogState = dialogState,
        onConfirmWithConflictCheck = onConfirmWithConflictCheck,
        onSelectModeChange = onSelectModeChange,
        onResetSelection = onResetSelection,
        onConfirmDownload = onConfirmDownload,
        onEnterUploadMode = onEnterUploadMode,
    )
    BottomButtonDialogHost(
        dialogState = dialogState,
        pendingConflict = pendingConflict,
        selectedList = selectedList,
        onResetSelection = onResetSelection,
        onZip = onZip,
        onRename = onRename,
        onDelete = onDelete,
        onResolveConflict = onResolveConflict,
    )
}
