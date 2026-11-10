package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameConflictDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.ZipDialog
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename

/**
 * dialogState 플래그와 ViewModel의 충돌 상태에 따라 파일 작업 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 입력·확인용 다이얼로그 open/close 상태 홀더
 * @param pendingConflict ViewModel이 보유한 사용자 확인 대기 충돌. null이면 충돌 다이얼로그를 표시하지 않음
 * @param selectedList 현재 선택된 파일 목록
 * @param info 선택 항목 정보 다이얼로그의 표시 값과 콜백 묶음
 * @param onResetSelection 작업 실행 후 선택 모드·선택 목록을 초기화하는 콜백
 * @param zip 압축 다이얼로그의 콜백 묶음
 * @param rename 이름 변경 다이얼로그의 콜백 묶음
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onDelete 삭제 실행 콜백
 * @param onResolveConflict 충돌 처리 방법을 전달하는 콜백 — 작업을 실행했으면 true를 반환
 */
@Composable
fun BottomButtonDialogHost(
    dialogState: BottomButtonDialogState,
    pendingConflict: PendingConflict?,
    selectedList: List<FileHolderItem>,
    info: SelectionInfoActions,
    onResetSelection: () -> Unit,
    zip: ZipActions,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (List<FileHolderItem>) -> Unit,
    onResolveConflict: (ConflictResolution) -> Boolean,
) {
    if (dialogState.zipDialog) {
        // 키 없음 — 연 회차마다 한 번만 제안한다. 입력란은 첫 구성의 값만 쓰므로, 리컴포지션마다 목록을 다시 훑을 필요가 없다
        val suggestedName = remember { zip.suggestName(selectedList) }
        ZipDialog(
            initialText = suggestedName,
            onConfirm = {
                zip.onZip(selectedList, it)
                onResetSelection()
            },
            onDismiss = { dialogState.dismissZip() },
            validate = zip.validateName
        )
    }
    if (dialogState.renameDialog) {
        RenameDialog(
            currentName = selectedList.first().name,
            isDirectory = selectedList.first().isDirectory,
            onConfirm = { rename.onRename(selectedList.first(), it) },
            onDismiss = { dialogState.dismissRename() },
            validate = { name -> rename.validate(selectedList.first(), name) }
        )
    }
    pendingRename?.let { pending ->
        RenameConflictDialog(
            pending = pending,
            isRemote = false,
            onConfirm = rename.onConfirmOverwrite,
            onDismiss = rename.onCancelOverwrite
        )
    }
    info.summary()?.let { summary ->
        InfoDialog(
            summary = summary,
            onDismiss = info.onDismiss
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
