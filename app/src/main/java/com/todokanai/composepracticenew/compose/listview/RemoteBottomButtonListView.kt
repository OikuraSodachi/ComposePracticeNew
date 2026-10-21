package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/**
 * renameTarget이 설정된 경우 이름 변경 다이얼로그를 렌더링한다.
 *
 * @param renameTarget 이름을 변경할 대상 파일, null이면 다이얼로그를 표시하지 않음
 * @param onDismiss 다이얼로그 닫기 콜백
 * @param onRename 이름 변경 확인 시 실행할 콜백
 * @param validateRename 대상 항목과 새 이름을 검증해 위반한 규칙 집합을 반환하는 함수
 * @param title 다이얼로그 제목 문자열
 */
@Composable
private fun RemoteRenameDialogHost(
    renameTarget: FileHolderItem?,
    onDismiss: () -> Unit,
    onRename: (FileHolderItem, String) -> Unit,
    validateRename: (FileHolderItem, String) -> Set<FileNameError>,
    title: String,
) {
    renameTarget?.let { target ->
        FileNameDialog(
            title = title,
            hint = target.name,
            initialText = target.name,
            validate = { name -> validateRename(target, name) },
            onConfirm = { newName -> onRename(target, newName) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * 원격 파일 목록 하단 버튼 영역 — 멀티셀렉트 모드에서 다운로드·삭제·이름변경을 제공한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 선택 모드
 * @param onSelectModeChange 선택 모드 변경 요청 콜백
 * @param onClearSelection 선택 목록 초기화 콜백
 * @param selectedList 선택 목록을 반환하는 함수 — 이 컴포저블에서 읽어 선택 변경 시 리컴포지션을 하단 버튼 영역으로 한정한다
 * @param onRename 이름 변경 실행 콜백
 * @param validateRename 이름을 바꿀 항목과 새 이름을 검증해 위반한 규칙 집합을 반환하는 함수(같은 폴더의 종류가 다른 같은 이름 항목 포함)
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onConfirmRename 이름 변경 덮어쓰기 확인 콜백
 * @param onCancelRename 이름 변경 덮어쓰기 취소 콜백
 * @param onDelete 선택 파일 일괄 삭제 실행 콜백
 * @param onEnterDownloadMode 다운로드 모드 진입 콜백
 * @param onConfirmUpload 업로드 확인 콜백
 */
@Composable
fun RemoteBottomButtonListView(
    modifier: Modifier,
    selectMode: RemoteSelectMode,
    onSelectModeChange: (RemoteSelectMode) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: () -> List<FileHolderItem>,
    onRename: (FileHolderItem, String) -> Unit,
    validateRename: (FileHolderItem, String) -> Set<FileNameError>,
    pendingRename: PendingRename?,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
    onEnterDownloadMode: (List<FileHolderItem>) -> Unit = {},
    onConfirmUpload: () -> Unit = {},
) {
    val currentSelectedList = selectedList()
    var renameTarget by remember { mutableStateOf<FileHolderItem?>(null) }
    // 삭제 대상은 coordinator의 선택 목록을 그대로 사용 — 확인 전까지 선택을 유지하므로 플래그만 보관한다
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val strRemoteDownload = stringResource(R.string.btn_remote_download)
    val strRemoteDelete   = stringResource(R.string.btn_remote_delete)
    val strRemoteRename   = stringResource(R.string.btn_remote_rename)

    RemoteRenameDialogHost(
        renameTarget = renameTarget,
        onDismiss    = { renameTarget = null },
        onRename     = onRename,
        validateRename = validateRename,
        title        = strRemoteRename,
    )
    pendingRename?.let { pending ->
        RenameConflictDialog(
            pending = pending,
            isRemote = true,
            onConfirm = onConfirmRename,
            onDismiss = onCancelRename
        )
    }
    if (showDeleteDialog) {
        DeleteDialog(
            onConfirm = {
                onDelete(currentSelectedList.toList())
                onSelectModeChange(SelectMode.Default)
                onClearSelection()
            },
            onDismiss = { showDeleteDialog = false }
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
                TextButton(onClick = {
                    onEnterDownloadMode(currentSelectedList.toList())
                    onSelectModeChange(SelectMode.Default)
                    onClearSelection()
                }) { Text(strRemoteDownload) }
                TextButton(
                    enabled = currentSelectedList.isNotEmpty(),
                    onClick = { showDeleteDialog = true }
                ) { Text(strRemoteDelete) }
                TextButton(
                    enabled = currentSelectedList.size == 1,
                    onClick = {
                        renameTarget = currentSelectedList.first()
                        onSelectModeChange(SelectMode.Default)
                        onClearSelection()
                    }
                ) { Text(strRemoteRename) }
            }
        }
        SelectMode.Upload -> {
            ConfirmButtons(
                modifier = modifier,
                // 선택 초기화는 실제 실행 시점에 coordinator가 담당 — 충돌 확인을 취소하면 업로드 확인 모드가 유지된다
                confirm = onConfirmUpload,
                cancel = { onSelectModeChange(SelectMode.Default) },
                mode = SelectMode.Upload
            )
        }
        SelectMode.Default -> Unit
    }
}
