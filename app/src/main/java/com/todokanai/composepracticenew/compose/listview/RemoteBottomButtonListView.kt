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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/**
 * renameTarget이 설정된 경우 이름 변경 다이얼로그를 렌더링한다.
 *
 * @param renameTarget 이름을 변경할 대상 파일, null이면 다이얼로그를 표시하지 않음
 * @param onDismiss 다이얼로그 닫기 콜백
 * @param onRename 이름 변경 확인 시 실행할 콜백
 * @param title 다이얼로그 제목 문자열
 */
@Composable
private fun RemoteRenameDialogHost(
    renameTarget: FileHolderItem?,
    onDismiss: () -> Unit,
    onRename: (FileHolderItem, String) -> Unit,
    title: String,
) {
    renameTarget?.let { target ->
        EditTextDialog(
            modifier = Modifier,
            title = title,
            defaultText = target.name,
            initialText = target.name,
            onConfirm = { newName -> onRename(target, newName) },
            onCancel = onDismiss,
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
 * @param selectedList 현재 선택된 파일 목록
 * @param onRename 이름 변경 실행 콜백
 * @param onDelete 단일 파일 삭제 실행 콜백
 * @param onEnterDownloadMode 다운로드 모드 진입 콜백
 * @param onConfirmUpload 업로드 확인 콜백
 */
@Composable
fun RemoteBottomButtonListView(
    modifier: Modifier,
    selectMode: SelectMode,
    onSelectModeChange: (SelectMode) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: List<FileHolderItem>,
    onRename: (FileHolderItem, String) -> Unit,
    onDelete: (FileHolderItem) -> Unit,
    onEnterDownloadMode: (List<FileHolderItem>) -> Unit = {},
    onConfirmUpload: () -> Unit = {},
) {
    var renameTarget by remember { mutableStateOf<FileHolderItem?>(null) }

    val strRemoteDownload = stringResource(R.string.btn_remote_download)
    val strRemoteDelete   = stringResource(R.string.btn_remote_delete)
    val strRemoteRename   = stringResource(R.string.btn_remote_rename)

    RemoteRenameDialogHost(
        renameTarget = renameTarget,
        onDismiss    = { renameTarget = null },
        onRename     = onRename,
        title        = strRemoteRename,
    )

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
                    onEnterDownloadMode(selectedList.toList())
                    onSelectModeChange(SelectMode.Default)
                    onClearSelection()
                }) { Text(strRemoteDownload) }
                TextButton(onClick = {
                    selectedList.forEach { onDelete(it) }
                    onSelectModeChange(SelectMode.Default)
                    onClearSelection()
                }) { Text(strRemoteDelete) }
                TextButton(
                    enabled = selectedList.size == 1,
                    onClick = {
                        renameTarget = selectedList.first()
                        onSelectModeChange(SelectMode.Default)
                        onClearSelection()
                    }
                ) { Text(strRemoteRename) }
            }
        }
        SelectMode.Confirm.Upload -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = {
                    onConfirmUpload()
                    onSelectModeChange(SelectMode.Default)
                    onClearSelection()
                },
                cancel = { onSelectModeChange(SelectMode.Default) },
                mode = SelectMode.Confirm.Upload
            )
        }
        // 원격 화면에서는 업로드 외의 확인 모드를 사용하지 않는다
        SelectMode.Default,
        SelectMode.Confirm.Copy,
        SelectMode.Confirm.Move,
        SelectMode.Confirm.Unzip,
        SelectMode.Confirm.UnzipHere,
        SelectMode.Confirm.Download -> Unit
    }
}
