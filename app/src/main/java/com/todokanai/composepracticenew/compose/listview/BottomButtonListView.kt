package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/** 하단 파일 작업 버튼(ModeAwareButtons)과 관련 다이얼로그(BottomButtonDialogHost)를 조합한다. */
@Composable
fun BottomButtonListView(
    modifier: Modifier,
    selectMode: Int,
    onSelectModeChange: (Int) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: List<FileHolderItem>,
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    onGetConflicts: (List<FileHolderItem>, Int) -> List<FileHolderItem>,
    onConfirm: (List<FileHolderItem>, Int, List<FileHolderItem>) -> Unit,
    onZip: (List<FileHolderItem>, String) -> Unit,
    onRename: (FileHolderItem, String) -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
) {
    val dialogState = rememberBottomButtonDialogState()

    // rememberUpdatedState — 람다를 재생성하지 않고 실행 시점에 최신 값을 읽도록 보장
    val latestSelectedList by rememberUpdatedState(selectedList)
    val latestSelectMode   by rememberUpdatedState(selectMode)

    // selectedList·selectMode·dialogState를 연결하는 조율 람다 — ModeAwareButtons와 BottomButtonDialogHost 어느 쪽에도 귀속되지 않으므로 여기 유지
    val onConfirmWithConflictCheck: () -> Unit = remember {
        {
            val conflicts = onGetConflicts(latestSelectedList, latestSelectMode)
            if (conflicts.isEmpty()) {
                onConfirm(latestSelectedList, latestSelectMode, emptyList())
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
            } else {
                dialogState.conflictFiles = conflicts
                dialogState.pendingSelectMode = latestSelectMode
                dialogState.showConflictDialog = true
            }
        }
    }

    ModeAwareButtons(
        modifier = modifier,
        selectMode = selectMode,
        selectedList = selectedList,
        dialogState = dialogState,
        onConfirmWithConflictCheck = onConfirmWithConflictCheck,
        onSelectModeChange = onSelectModeChange,
        onClearSelection = onClearSelection,
        onConfirmDownload = onConfirmDownload,
        onEnterUploadMode = onEnterUploadMode,
    )
    BottomButtonDialogHost(
        dialogState = dialogState,
        selectedList = selectedList,
        onSelectModeChange = onSelectModeChange,
        onClearSelection = onClearSelection,
        onZip = onZip,
        onRename = onRename,
        onDelete = onDelete,
        onConfirm = onConfirm,
    )
}
