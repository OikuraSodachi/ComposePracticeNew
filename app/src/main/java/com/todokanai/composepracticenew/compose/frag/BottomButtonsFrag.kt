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
import com.todokanai.composepracticenew.viewmodel.BottomButtonsViewModel
import java.io.File

@Composable
fun BottomButtonsFrag(
    modifier: Modifier,
    selectedList:List<File>,
    viewModel:BottomButtonsViewModel
){
    val currentPath = viewModel.currentPath.collectAsStateWithLifecycle()

    var zipDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember{ mutableStateOf(false) }

    BottomButtonListView(
        modifier = modifier,
        selectModeFlow = viewModel.selectMode,
        zipDialog = { zipDialog = true },
        renameDialog = { renameDialog = true },
        infoDialog = { infoDialog = true },
        moveMode = { viewModel.moveMode() },
        copyMode = { viewModel.copyMode() },
        unzipMode = { viewModel.unzipMode() },
        unzipHereMode = { viewModel.unzipHereMode() },
        delete = { deleteDialog = true },
        cancel = { viewModel.cancel() },
        //confirm = { viewModel.confirm(selectedList, currentPath.value) },
        confirm = { viewModel.confirm(currentPath.value) },
        selectedList = selectedList
    )
    if(zipDialog){
        ZipDialog(
            onConfirm = { viewModel.zip(it) },
            onCancel =  { zipDialog = false }
        )
    }
    if(renameDialog){
        RenameDialog(
            onConfirm = { viewModel.rename(it) },
            onCancel = {renameDialog = false}
        )
    }
    if(infoDialog){
        InfoDialog(
            files = selectedList,
            onCancel =  { infoDialog = false }
        )
    }
    if(deleteDialog){
        DeleteDialog(
            onConfirm = { viewModel.delete() },
            onCancel = {deleteDialog = false}
        )
    }
}