package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameConflictDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.CompressDialog
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * dialogState 플래그와 ViewModel의 확인 대기 상태에 따라 파일 작업 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 입력·확인용 다이얼로그 open/close 상태 홀더
 * @param selectedList 현재 선택된 파일 목록
 * @param info 선택 항목 정보 다이얼로그의 표시 값과 콜백 묶음
 * @param onSubmitSelection 확인한 작업의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param compress 압축 다이얼로그의 콜백 묶음
 * @param rename 이름 변경 다이얼로그의 콜백 묶음
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onDelete 삭제 실행 콜백
 */
@Composable
fun BottomButtonDialogHost(
    dialogState: BottomButtonDialogState,
    selectedList: List<FileHolderItem>,
    info: SelectionInfoActions,
    onSubmitSelection: () -> SubmittedSelection?,
    compress: CompressActions,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (SubmittedSelection) -> Unit,
) {
    // openId를 키로 — 연 회차마다 입력 상태(rememberSaveable)를 분리해, 화면 이동·프로세스 종료 때 저장된 이전 입력값이
    // 새로 연 다이얼로그에 복원되지 않게 한다. 회전 시에는 openId가 그대로라 입력값이 유지된다
    key(dialogState.openId) {
        dialogState.compressTarget?.let { folder ->
            // key(openId) 안에서 키 없이 remember — 연 회차마다 한 번만 제안한다. 입력란은 첫 구성의 값만 쓰므로, 리컴포지션마다 목록을 다시 훑을 필요가 없다
            val suggestedName = remember { compress.suggestName(selectedList) }
            CompressDialog(
                initialText = suggestedName,
                onConfirm = { name -> onSubmitSelection()?.let { compress.onCompress(it, folder, name) } },
                onDismiss = { dialogState.dismissCompress() },
                validate = { name -> compress.validateName(folder, name) }
            )
        }
        if (dialogState.renameDialog) {
            RenameDialog(
                currentName = selectedList.first().name,
                isDirectory = selectedList.first().isDirectory,
                onConfirm = { name -> onSubmitSelection()?.let { rename.onRename(it, name) } },
                onDismiss = { dialogState.dismissRename() },
                validate = { name -> rename.validate(selectedList.first(), name) }
            )
        }
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
            onConfirm = { onSubmitSelection()?.let(onDelete) },
            onDismiss = { dialogState.dismissDelete() }
        )
    }
}
