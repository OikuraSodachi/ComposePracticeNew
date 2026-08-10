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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.myobjects.Constants
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
    viewModel: RemoteFileListViewModel = hiltViewModel()
) {
    var renameTarget by remember { mutableStateOf<FileHolderItem?>(null) }

    renameTarget?.let { target ->
        EditTextDialog(
            modifier = Modifier,
            title = "이름 변경",
            defaultText = target.name,
            initialText = target.name,
            onConfirm = { newName -> viewModel.onRename(target, newName) },
            onCancel = { renameTarget = null }
        )
    }

    if (selectMode == Constants.MULTI_SELECT_MODE) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                selectedList.forEach { viewModel.onDownload(it) }
                onSelectModeChange(Constants.DEFAULT_MODE)
                onClearSelection()
            }) {
                Text("다운로드")
            }
            TextButton(onClick = {
                selectedList.forEach { viewModel.onDelete(it) }
                onSelectModeChange(Constants.DEFAULT_MODE)
                onClearSelection()
            }) {
                Text("삭제")
            }
            TextButton(
                enabled = selectedList.size == 1,
                onClick = {
                    renameTarget = selectedList.first()
                    onSelectModeChange(Constants.DEFAULT_MODE)
                    onClearSelection()
                }
            ) {
                Text("이름 변경")
            }
        }
    }
}
