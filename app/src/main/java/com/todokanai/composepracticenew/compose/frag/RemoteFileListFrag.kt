package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/** 원격 스토리지의 파일 목록을 표시하고 I/O 작업(다운로드·업로드·이름변경·삭제·새폴더)을 제공하는 화면. */
@Composable
fun RemoteFileListFrag(
    modifier: Modifier,
    selectMode: Int,
    selectedList: List<FileHolderItem>,
    onSelectModeChange: (Int) -> Unit,
    onSelectedListChange: (List<FileHolderItem>) -> Unit,
    onSwitchToLocal: () -> Unit = {},
    viewModel: RemoteFileListViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<FileHolderItem?>(null) }
    var newFolderInput by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectMode == Constants.MULTI_SELECT_MODE) {
        onSelectModeChange(Constants.DEFAULT_MODE)
        onSelectedListChange(emptyList())
    }

    // 이름 변경 다이얼로그
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

    // 새 폴더 다이얼로그
    newFolderInput?.let { folderName ->
        AlertDialog(
            onDismissRequest = { newFolderInput = null },
            title = { Text("새 폴더") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { newFolderInput = it },
                    singleLine = true,
                    label = { Text("폴더 이름") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onMakeDirectory(folderName)
                        newFolderInput = null
                    },
                    enabled = folderName.isNotBlank()
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { newFolderInput = null }) { Text("취소") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.value.fileHolderItemList.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentSize(),
                text = stringResource(R.string.empty_directory)
            )
        } else {
            FileListView(
                modifier = Modifier.weight(1f),
                fileHolderItemList = uiState.value.fileHolderItemList,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it) },
                onItemLongClick = { onSelectModeChange(Constants.MULTI_SELECT_MODE) },
                addToList = { onSelectedListChange(selectedList + it) },
                removeFromList = { onSelectedListChange(selectedList - it) },
                clearList = { onSelectedListChange(emptyList()) }
            )
        }

        when (selectMode) {
            Constants.DEFAULT_MODE -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.onUpload("") }) {
                    Text("업로드")
                }
                TextButton(onClick = { newFolderInput = "" }) {
                    Text("새 폴더")
                }
            }
            Constants.MULTI_SELECT_MODE -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    selectedList.forEach { viewModel.onDownload(it) }
                    onSelectModeChange(Constants.DEFAULT_MODE)
                    onSelectedListChange(emptyList())
                }) {
                    Text("다운로드")
                }
                TextButton(onClick = {
                    selectedList.forEach { viewModel.onDelete(it) }
                    onSelectModeChange(Constants.DEFAULT_MODE)
                    onSelectedListChange(emptyList())
                }) {
                    Text("삭제")
                }
                TextButton(
                    enabled = selectedList.size == 1,
                    onClick = {
                        renameTarget = selectedList.first()
                        onSelectModeChange(Constants.DEFAULT_MODE)
                        onSelectedListChange(emptyList())
                    }
                ) {
                    Text("이름 변경")
                }
            }
        }

        StorageSwitchBar(
            isRemote = true,
            onSwitchToLocal = onSwitchToLocal,
            onSwitchToRemote = {}
        )
    }
}
