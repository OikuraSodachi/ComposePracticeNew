package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameConflictDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.ZipDialog
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.SelectionSummary

/**
 * dialogState 플래그와 ViewModel의 충돌 상태에 따라 파일 작업 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 입력·확인용 다이얼로그 open/close 상태 홀더
 * @param pendingConflict ViewModel이 보유한 사용자 확인 대기 충돌. null이면 충돌 다이얼로그를 표시하지 않음
 * @param selectedList 현재 선택된 파일 목록
 * @param selectionSummary 선택 목록 요약을 반환하는 함수 — 정보 다이얼로그가 열렸을 때만 읽는다
 * @param onResetSelection 작업 실행 후 선택 모드·선택 목록을 초기화하는 콜백
 * @param onZip 압축 실행 콜백
 * @param validateZipName 압축 파일 이름(확장자 제외)을 검증해 위반한 규칙 집합을 반환하는 함수
 * @param onRename 이름 변경 실행 콜백
 * @param validateRename 대상 항목과 새 이름을 검증해 위반한 규칙 집합을 반환하는 함수(같은 폴더의 종류가 다른 같은 이름 항목 포함)
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onConfirmRename 이름 변경 덮어쓰기(합치기) 확인 콜백
 * @param onCancelRename 이름 변경 덮어쓰기 취소 콜백
 * @param onDelete 삭제 실행 콜백
 * @param onResolveConflict 충돌 처리 방법을 전달하는 콜백 — 작업을 실행했으면 true를 반환
 */
@Composable
fun BottomButtonDialogHost(
    dialogState: BottomButtonDialogState,
    pendingConflict: PendingConflict?,
    selectedList: List<FileHolderItem>,
    selectionSummary: () -> SelectionSummary,
    onResetSelection: () -> Unit,
    onZip: (List<FileHolderItem>, String) -> Unit,
    validateZipName: (String) -> Set<FileNameError>,
    onRename: (FileHolderItem, String) -> Unit,
    validateRename: (FileHolderItem, String) -> Set<FileNameError>,
    pendingRename: PendingRename?,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
    onResolveConflict: (ConflictResolution) -> Boolean,
) {
    if (dialogState.zipDialog) {
        ZipDialog(
            onConfirm = {
                onZip(selectedList, it)
                onResetSelection()
            },
            onDismiss = { dialogState.dismissZip() },
            validate = validateZipName
        )
    }
    if (dialogState.renameDialog) {
        RenameDialog(
            onConfirm = { onRename(selectedList.first(), it) },
            onDismiss = { dialogState.dismissRename() },
            validate = { name -> validateRename(selectedList.first(), name) }
        )
    }
    pendingRename?.let { pending ->
        RenameConflictDialog(
            pending = pending,
            isRemote = false,
            onConfirm = onConfirmRename,
            onDismiss = onCancelRename
        )
    }
    if (dialogState.infoDialog) {
        InfoDialog(
            summary = selectionSummary(),
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
            destinationUnconfirmed = conflict.destinationUnconfirmed,
            onOverwrite = { if (onResolveConflict(ConflictResolution.OVERWRITE)) onResetSelection() },
            onSkip = { if (onResolveConflict(ConflictResolution.SKIP)) onResetSelection() },
            onCancel = { onResolveConflict(ConflictResolution.CANCEL) }
        )
    }
}
