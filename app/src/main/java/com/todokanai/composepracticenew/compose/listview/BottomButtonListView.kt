package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.compose.BottomButtons
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.FileConflictDialog
import com.todokanai.composepracticenew.compose.dialog.InfoDialog
import com.todokanai.composepracticenew.compose.dialog.RenameDialog
import com.todokanai.composepracticenew.compose.dialog.ZipDialog
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.BottomButtonsViewModel

@Composable
fun BottomButtonListView(
    modifier: Modifier,
    selectMode: Int,
    onSelectModeChange: (Int) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: List<FileHolderItem>,
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    viewModel: BottomButtonsViewModel = hiltViewModel()
) {
    var zipDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictFiles by remember { mutableStateOf<List<FileHolderItem>>(emptyList()) }
    var pendingSelectMode by remember { mutableStateOf(AppConstants.DEFAULT_MODE) }

    val onConfirmWithConflictCheck: () -> Unit = {
        val conflicts = viewModel.getConflicts(selectedList, selectMode)
        if (conflicts.isEmpty()) {
            viewModel.confirm(selectedList, selectMode)
            onSelectModeChange(AppConstants.DEFAULT_MODE)
            onClearSelection()
        } else {
            conflictFiles = conflicts
            pendingSelectMode = selectMode
            showConflictDialog = true
        }
    }
    val onCancel: () -> Unit = { onSelectModeChange(AppConstants.DEFAULT_MODE) }

    when (selectMode) {
        AppConstants.MULTI_SELECT_MODE -> {
            BottomButtons(
                modifier = modifier,
                move = { onSelectModeChange(AppConstants.CONFIRM_MODE_MOVE) },
                copy = { onSelectModeChange(AppConstants.CONFIRM_MODE_COPY) },
                delete = { deleteDialog = true },
                zip = { zipDialog = true },
                unzip = { onSelectModeChange(AppConstants.CONFIRM_MODE_UNZIP) },
                unzipHere = { onSelectModeChange(AppConstants.CONFIRM_MODE_UNZIP_HERE) },
                rename = { renameDialog = true },
                info = { infoDialog = true },
                upload = {
                    onEnterUploadMode()
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                },
                selectedList = selectedList
            )
        }
        AppConstants.CONFIRM_MODE_MOVE -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = onConfirmWithConflictCheck,
                cancel = onCancel,
                mode = AppConstants.CONFIRM_MODE_MOVE
            )
        }
        AppConstants.CONFIRM_MODE_COPY -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = onConfirmWithConflictCheck,
                cancel = onCancel,
                mode = AppConstants.CONFIRM_MODE_COPY
            )
        }
        AppConstants.CONFIRM_MODE_UNZIP, AppConstants.CONFIRM_MODE_UNZIP_HERE -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = onConfirmWithConflictCheck,
                cancel = onCancel,
                mode = selectMode
            )
        }
        AppConstants.CONFIRM_MODE_DOWNLOAD -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = {
                    onConfirmDownload()
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                },
                cancel = { onSelectModeChange(AppConstants.DEFAULT_MODE) },
                mode = AppConstants.CONFIRM_MODE_DOWNLOAD
            )
        }
    }

    if (zipDialog) {
        ZipDialog(
            onConfirm = {
                viewModel.zip(selectedList, it)
                onSelectModeChange(AppConstants.DEFAULT_MODE)
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
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
            },
            onCancel = { deleteDialog = false }
        )
    }
    if (showConflictDialog) {
        FileConflictDialog(
            conflictingFiles = conflictFiles,
            totalCount = selectedList.size,
            onOverwrite = {
                viewModel.confirm(selectedList, pendingSelectMode)
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
                showConflictDialog = false
            },
            onSkip = {
                viewModel.confirm(selectedList, pendingSelectMode, skipFiles = conflictFiles)
                onSelectModeChange(AppConstants.DEFAULT_MODE)
                onClearSelection()
                showConflictDialog = false
            },
            onCancel = { showConflictDialog = false }
        )
    }
}
