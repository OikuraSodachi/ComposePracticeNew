package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.ZipDialog
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict

/**
 * dialogState 플래그와 ViewModel의 충돌 상태에 따라 파일 작업 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 입력·확인용 다이얼로그 open/close 상태 홀더
 * @param pendingConflict ViewModel이 보유한 사용자 확인 대기 충돌. null이면 충돌 다이얼로그를 표시하지 않음
 * @param selectedList 현재 선택된 파일 목록
 * @param onResetSelection 작업 실행 후 선택 모드·선택 목록을 초기화하는 콜백
 * @param onZip 압축 실행 콜백
 * @param onRename 이름 변경 실행 콜백
 * @param onDelete 삭제 실행 콜백
 * @param onResolveConflict 충돌 처리 방법을 전달하는 콜백 — 작업을 실행했으면 true를 반환
 */
@Composable
fun BottomButtonDialogHost(
    dialogState: BottomButtonDialogState,
    pendingConflict: PendingConflict?,
    selectedList: List<FileHolderItem>,
    onResetSelection: () -> Unit,
    onZip: (List<FileHolderItem>, String) -> Unit,
    onRename: (FileHolderItem, String) -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
    onResolveConflict: (ConflictResolution) -> Boolean,
) {
    if (dialogState.zipDialog) {
        ZipDialog(
            onConfirm = {
                onZip(selectedList, it)
                onResetSelection()
            },
            onDismiss = { dialogState.dismissZip() }
        )
    }
    if (dialogState.renameDialog) {
        RenameDialog(
            onConfirm = { onRename(selectedList.first(), it) },
            onDismiss = { dialogState.dismissRename() }
        )
    }
    if (dialogState.infoDialog) {
        InfoDialog(
            files = selectedList,
            onDismiss = { dialogState.dismissInfo() }
        )
    }
    if (dialogState.deleteDialog) {
        DeleteDialog(
            onConfirm = {
                onDelete(selectedList)
                onResetSelection()
            },
            onDismiss = { dialogState.dismissDelete() }
        )
    }
    pendingConflict?.let { conflict ->
        FileConflictDialog(
            conflictingFiles = conflict.conflicts,
            totalCount = conflict.targets.size,
            onOverwrite = { if (onResolveConflict(ConflictResolution.OVERWRITE)) onResetSelection() },
            onSkip = { if (onResolveConflict(ConflictResolution.SKIP)) onResetSelection() },
            onCancel = { onResolveConflict(ConflictResolution.CANCEL) }
        )
    }
}
