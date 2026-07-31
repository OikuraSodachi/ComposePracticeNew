package com.todokanai.composepracticenew.compose.frag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.ZipDialog
import com.todokanai.composepracticenew.compose.listview.BottomButtonListView
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.viewmodel.BottomButtonsViewModel
import java.io.File

@Composable
fun BottomButtonsFrag(
    modifier: Modifier,
    selectedList: List<File>,
    selectMode: Int,
    onSelectModeChange: (Int) -> Unit,
    onClearSelection: () -> Unit,
    viewModel: BottomButtonsViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var zipDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    BottomButtonListView(
        modifier = modifier,
        selectMode = selectMode,
        zipDialog = { zipDialog = true },
        renameDialog = { renameDialog = true },
        infoDialog = { infoDialog = true },
        moveMode = { onSelectModeChange(Constants.CONFIRM_MODE_MOVE) },
        copyMode = { onSelectModeChange(Constants.CONFIRM_MODE_COPY) },
        unzipMode = { onSelectModeChange(Constants.CONFIRM_MODE_UNZIP) },
        unzipHereMode = { onSelectModeChange(Constants.CONFIRM_MODE_UNZIP_HERE) },
        delete = { deleteDialog = true },
        cancel = { onSelectModeChange(Constants.DEFAULT_MODE) },
        confirm = {
            viewModel.confirm(selectedList, selectMode, uiState.value.currentPath)
            onSelectModeChange(Constants.DEFAULT_MODE)
            onClearSelection()
        },
        selectedList = selectedList
    )
    if (zipDialog) {
        ZipDialog(
            onConfirm = {
                viewModel.zip(selectedList, it)
                onSelectModeChange(Constants.DEFAULT_MODE)
                onClearSelection()
            },
            onCancel = { zipDialog = false }
        )
    }
    if (renameDialog) {
        RenameDialog(
            onConfirm = { viewModel.rename(selectedList.first(), it) },
            onCancel = { renameDialog = false }
        )
    }
    if (infoDialog) {
        InfoDialog(
            files = selectedList,
            onCancel = { infoDialog = false }
        )
    }
    if (deleteDialog) {
        DeleteDialog(
            onConfirm = {
                viewModel.delete(selectedList)
                onSelectModeChange(Constants.DEFAULT_MODE)
                onClearSelection()
            },
            onCancel = { deleteDialog = false }
        )
    }
}
