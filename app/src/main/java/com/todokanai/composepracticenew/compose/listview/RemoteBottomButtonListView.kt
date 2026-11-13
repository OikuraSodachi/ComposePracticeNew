package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileNameDialog
import com.todokanai.composepracticenew.compose.dialog.RenameConflictDialog
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.RemoteSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * renameTarget이 설정된 경우 이름 변경 다이얼로그를 렌더링한다.
 *
 * @param renameTarget 이름을 변경할 대상 파일, null이면 다이얼로그를 표시하지 않음
 * @param onDismiss 다이얼로그 닫기 콜백
 * @param onConfirm 이름 변경 확인 시 새 이름을 전달받는 콜백
 * @param validateRename 대상 항목과 새 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 * @param title 다이얼로그 제목 문자열
 */
@Composable
private fun RemoteRenameDialogHost(
    renameTarget: FileHolderItem?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    validateRename: (FileHolderItem, String) -> Set<FileNameError>,
    title: String,
) {
    renameTarget?.let { target ->
        FileNameDialog(
            title = title,
            hint = target.name,
            initialText = target.name,
            selectBaseNameOnly = !target.isDirectory,
            validate = { name -> validateRename(target, name) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * 원격 파일 목록 하단 버튼 영역 — 멀티셀렉트 모드에서 다운로드·삭제·이름변경을 제공한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 선택 모드
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백 — 취소·뒤로가기용
 * @param onSubmitSelection 확인한 작업의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param selectedList 선택 목록을 반환하는 함수 — 이 컴포저블에서 읽어 선택 변경 시 리컴포지션을 하단 버튼 영역으로 한정한다
 * @param rename 이름 변경 다이얼로그의 콜백 묶음
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onDelete 선택에서 꺼낸 파일 일괄 삭제 실행 콜백
 * @param onEnterDownloadMode 원격 선택을 꺼내 다운로드 모드로 진입하는 콜백
 * @param onConfirmUpload 업로드 확인 콜백
 */
@Composable
fun RemoteBottomButtonListView(
    modifier: Modifier,
    selectMode: RemoteSelectMode,
    onResetSelection: () -> Unit,
    onSubmitSelection: () -> SubmittedSelection?,
    selectedList: () -> List<FileHolderItem>,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (SubmittedSelection) -> Unit,
    onEnterDownloadMode: () -> Unit,
    onConfirmUpload: () -> Unit,
) {
    val currentSelectedList = selectedList()
    /** 이 화면의 이름 변경·삭제 다이얼로그 열림 상태. */
    // 화면 단위 ViewModel에 보관된 다이얼로그 상태 — 대상인 coordinator의 선택 목록과 수명이 같아, 회전 시 유지되고
    // 화면 이동·프로세스 종료 후 복귀 시 닫힌다(rememberScreenDialogState 참고)
    val dialogState = rememberRemoteBottomButtonDialogState()

    val strRemoteDownload = stringResource(R.string.btn_remote_download)
    val strRemoteDelete   = stringResource(R.string.btn_remote_delete)
    val strRemoteRename   = stringResource(R.string.btn_remote_rename)

    // openId를 키로 — 연 회차마다 입력 상태(rememberSaveable)를 분리해, 화면 이동·프로세스 종료 때 저장된 이전 입력값이
    // 새로 연 다이얼로그에 복원되지 않게 한다. 회전 시에는 openId가 그대로라 입력값이 유지된다
    key(dialogState.openId) {
        RemoteRenameDialogHost(
            // 선택이 하나가 아니게 되면(끊김으로 선택이 버려지는 등) 대상이 없으므로 띄우지 않는다
            renameTarget = currentSelectedList.singleOrNull()?.takeIf { dialogState.renameDialog },
            onDismiss    = dialogState::dismissRename,
            onConfirm    = { newName -> onSubmitSelection()?.let { rename.onRename(it, newName) } },
            validateRename = rename.validate,
            title        = strRemoteRename,
        )
    }
    pendingRename?.let { pending ->
        RenameConflictDialog(
            pending = pending,
            isRemote = true,
            onConfirm = rename.onConfirmOverwrite,
            onDismiss = rename.onCancelOverwrite
        )
    }
    if (dialogState.deleteDialog) {
        DeleteDialog(
            onConfirm = { onSubmitSelection()?.let(onDelete) },
            onDismiss = dialogState::dismissDelete
        )
    }

    when (selectMode) {
        SelectMode.MultiSelect -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEnterDownloadMode) { Text(strRemoteDownload) }
                TextButton(
                    enabled = currentSelectedList.isNotEmpty(),
                    onClick = dialogState::openDelete
                ) { Text(strRemoteDelete) }
                TextButton(
                    enabled = currentSelectedList.size == 1,
                    onClick = dialogState::openRename
                ) { Text(strRemoteRename) }
            }
        }
        SelectMode.Upload -> {
            ConfirmButtons(
                modifier = modifier,
                // 선택 초기화는 실제 실행 시점에 coordinator가 담당 — 충돌 확인을 취소하면 업로드 확인 모드가 유지된다
                confirm = onConfirmUpload,
                cancel = onResetSelection,
                mode = SelectMode.Upload
            )
        }
        SelectMode.Default -> Unit
    }
}
