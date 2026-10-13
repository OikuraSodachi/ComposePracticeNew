package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.ZipDialog
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/**
 * dialogState의 플래그에 따라 5개의 파일 작업 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 다이얼로그 open/close 및 충돌 파일 상태 홀더
 * @param selectedList 현재 선택된 파일 목록
 * @param onSelectModeChange 선택 모드 변경 요청 콜백
 * @param onClearSelection 선택 목록 초기화 콜백
 * @param onZip 압축 실행 콜백
 * @param onRename 이름 변경 실행 콜백
 * @param onDelete 삭제 실행 콜백
 * @param onConfirm 이동/복사/압축 해제 확인 실행 콜백 — 세 번째 인자는 건너뛸 충돌 파일 목록(overwrite 시 emptyList())
 */
@Composable
fun BottomButtonDialogHost(
    dialogState: BottomButtonDialogState,
    selectedList: List<FileHolderItem>,
    onSelectModeChange: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onZip: (List<FileHolderItem>, String) -> Unit,
    onRename: (FileHolderItem, String) -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
    onConfirm: (List<FileHolderItem>, Int, List<FileHolderItem>) -> Unit,
) {
    if (dialogState.zipDialog) {
        ZipDialog(
            onConfirm = {
                onZip(selectedList, it)
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
            },
            onCancel = { dialogState.dismissZip() }
        )
    }
    if (dialogState.renameDialog) {
        RenameDialog(
            onConfirm = { onRename(selectedList.first(), it) },
            onCancel = { dialogState.dismissRename() }
        )
    }
    if (dialogState.infoDialog) {
        InfoDialog(
            files = selectedList,
            onCancel = { dialogState.dismissInfo() }
        )
    }
    if (dialogState.deleteDialog) {
        DeleteDialog(
            onConfirm = {
                onDelete(selectedList)
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
            },
            onCancel = { dialogState.dismissDelete() }
        )
    }
    if (dialogState.showConflictDialog) {
        FileConflictDialog(
            conflictingFiles = dialogState.conflictFiles,
            totalCount = selectedList.size,
            onOverwrite = {
                onConfirm(selectedList, dialogState.pendingSelectMode, emptyList())
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
                dialogState.dismissConflict()
            },
            onSkip = {
                onConfirm(selectedList, dialogState.pendingSelectMode, dialogState.conflictFiles)
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
                dialogState.dismissConflict()
            },
            onCancel = { dialogState.dismissConflict() }
        )
    }
}
