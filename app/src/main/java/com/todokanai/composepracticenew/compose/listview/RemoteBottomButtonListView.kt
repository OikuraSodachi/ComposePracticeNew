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
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/** 원격 파일 목록 하단 버튼 영역 — 멀티셀렉트 모드에서 다운로드·삭제·이름변경을 제공한다. */
@Composable
fun RemoteBottomButtonListView(
    modifier: Modifier,
    selectMode: Int,
    onSelectModeChange: (Int) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: List<FileHolderItem>,
    onEnterDownloadMode: (List<FileHolderItem>) -> Unit = {},
    onConfirmUpload: () -> Unit = {},
    viewModel: RemoteFileListViewModel = hiltViewModel()
) {
    var renameTarget by remember { mutableStateOf<FileHolderItem?>(null) }

    val strRemoteDownload = stringResource(R.string.btn_remote_download)
    val strRemoteDelete = stringResource(R.string.btn_remote_delete)
    val strRemoteRename = stringResource(R.string.btn_remote_rename)

    renameTarget?.let { target ->
        EditTextDialog(
            modifier = Modifier,
            title = strRemoteRename,
            defaultText = target.name,
            initialText = target.name,
            onConfirm = { newName -> viewModel.onRename(target, newName) },
            onCancel = { renameTarget = null }
        )
    }

    when (selectMode) {
        AppConstants.MULTI_SELECT_MODE -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    onEnterDownloadMode(selectedList.toList())
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                }) {
                    Text(strRemoteDownload)
                }
                TextButton(onClick = {
                    selectedList.forEach { viewModel.onDelete(it) }
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                }) {
                    Text(strRemoteDelete)
                }
                TextButton(
                    enabled = selectedList.size == 1,
                    onClick = {
                        renameTarget = selectedList.first()
                        onSelectModeChange(AppConstants.DEFAULT_MODE)
                        onClearSelection()
                    }
                ) {
                    Text(strRemoteRename)
                }
            }
        }
        AppConstants.CONFIRM_MODE_UPLOAD -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = {
                    onConfirmUpload()
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                },
                cancel = { onSelectModeChange(AppConstants.DEFAULT_MODE) },
                mode = AppConstants.CONFIRM_MODE_UPLOAD
            )
        }
    }
}
