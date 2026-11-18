package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.RenameConflictDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * 로컬·원격 목록 하단 버튼이 함께 쓰는 선택 항목 다이얼로그(이름 변경·덮어쓰기 확인·삭제)를 열림 상태에 따라 렌더링한다.
 * 열림 상태는 화면마다 다른 다이얼로그 상태 클래스가 보유하고, 이 컴포저블은 그 값과 콜백만 받는다.
 *
 * @param openId 다이얼로그를 연 회차의 id. 이름 변경 입력 상태를 회차마다 분리하는 키로 쓴다
 * @param selectedList 현재 선택된 파일 목록
 * @param renameOpen 이름 변경 다이얼로그 열림 여부. 대상은 선택 목록의 단일 항목이다
 * @param onDismissRename 이름 변경 다이얼로그를 닫는 콜백
 * @param deleteOpen 삭제 다이얼로그 열림 여부
 * @param onDismissDelete 삭제 다이얼로그를 닫는 콜백
 * @param isRemote 원격 저장소이면 true — 덮어쓰기 확인에 결과가 서버 동작에 따라 다르다는 안내를 보여준다
 * @param onSubmitSelection 확인한 삭제의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param rename 이름 변경 다이얼로그의 콜백 묶음
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onDelete 선택에서 꺼낸 항목의 삭제 실행 콜백
 */
@Composable
fun SelectionEntryDialogs(
    openId: Long,
    selectedList: List<FileHolderItem>,
    renameOpen: Boolean,
    onDismissRename: () -> Unit,
    deleteOpen: Boolean,
    onDismissDelete: () -> Unit,
    isRemote: Boolean,
    onSubmitSelection: () -> SubmittedSelection?,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (SubmittedSelection) -> Unit,
) {
    // openId를 키로 — 연 회차마다 입력 상태(rememberSaveable)를 분리해, 화면 이동·프로세스 종료 때 저장된 이전 입력값이
    // 새로 연 다이얼로그에 복원되지 않게 한다. 회전 시에는 openId가 그대로라 입력값이 유지된다
    key(openId) {
        // 대상은 선택에서만 구한다(#369) — 열림 플래그와 따로 두면 선택이 하나가 아니게 됐을 때 대상 없이 열린다
        selectedList.singleOrNull()?.takeIf { renameOpen }?.let { target ->
            RenameDialog(
                currentName = target.name,
                isDirectory = target.isDirectory,
                onConfirm = { name -> rename.onSubmitItem()?.let { rename.onRename(it, name) } },
                onDismiss = onDismissRename,
                validate = { name -> rename.validate(target, name) }
            )
        }
    }
    pendingRename?.let { pending ->
        RenameConflictDialog(
            pending = pending,
            isRemote = isRemote,
            onConfirm = rename.onConfirmOverwrite,
            onDismiss = rename.onCancelOverwrite
        )
    }
    if (deleteOpen) {
        DeleteDialog(
            onConfirm = { onSubmitSelection()?.let(onDelete) },
            onDismiss = onDismissDelete
        )
    }
}
