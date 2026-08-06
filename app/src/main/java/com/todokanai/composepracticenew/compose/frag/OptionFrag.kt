package com.todokanai.composepracticenew.compose.frag

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import kotlin.system.exitProcess
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.SortDialog
import com.todokanai.composepracticenew.compose.holder.DirectoryHolder
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.compose.presets.dropdownmenu.MyDropdownMenu
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.viewmodel.OptionViewModel
import com.todokanai.composepracticenew.ui.model.DirectoryItem

@Composable
fun OptionFrag(
    modifier: Modifier,
    activity: Activity,
    navigateToStorage: () -> Unit,
    dirTree: List<DirectoryItem>,
    onDirClick: (DirectoryItem) -> Unit,
    viewModel: OptionViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var showEditTextDialog by remember { mutableStateOf(false) }
    if (showEditTextDialog) {
        EditTextDialog(
            modifier = Modifier,
            title = stringResource(R.string.dialog_new_folder_title),
            defaultText = stringResource(R.string.dialog_new_folder_hint),
            onConfirm = {
                showEditTextDialog = false
                viewModel.newFolder(it)
            },
        ) { showEditTextDialog = false }
    }

    var showSortDialog by remember { mutableStateOf(false) }
    if (showSortDialog) {
        SortDialog(
            items = viewModel.sortModeCallbackList(),
            selectedItem = uiState.value.sortMode,
            onCancel = {
                showSortDialog = false
            }
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            TextButton(
                onClick = { navigateToStorage() },
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(stringResource(R.string.btn_storage))
            }

            val moreButtonExpanded = remember { mutableStateOf(false) }
            TextButton(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentSize(),
                onClick = { moreButtonExpanded.value = !moreButtonExpanded.value }
            ) {
                Text(stringResource(R.string.btn_more))

                MyDropdownMenu(
                    contents = listOf(
                        Pair(stringResource(R.string.btn_create_new_folder), { showEditTextDialog = true }),
                        Pair(stringResource(R.string.btn_sort), { showSortDialog = true }),
                        Pair(stringResource(R.string.btn_exit), { exitApp(activity) })
                    ),
                    expanded = moreButtonExpanded
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            items(dirTree.size) {
                val item = dirTree[it]
                DirectoryHolder(
                    modifier = Modifier
                        .clickable { onDirClick(item) },
                    pathName = item
                )
            }
        }
    }
}

private fun exitApp(activity: Activity) {
    val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.appTasks.forEach { it.finishAndRemoveTask() }
    exitProcess(0)
}
