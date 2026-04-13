package com.todokanai.composepracticenew.compose.frag

import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.dialog.SortDialog
import com.todokanai.composepracticenew.compose.presets.dialog.EditTextDialog
import com.todokanai.composepracticenew.compose.presets.dropdownmenu.MyDropdownMenu
import com.todokanai.composepracticenew.viewmodel.OptionViewModel

@Composable
fun OptionFrag(
    modifier: Modifier,
    activity:Activity,
    viewModel:OptionViewModel
) {

    val storageList = viewModel.storageList.collectAsStateWithLifecycle()

    val sortModeSelected = viewModel.sortMode.collectAsStateWithLifecycle()
    var showEditTextDialog by remember { mutableStateOf(false) }
    if(showEditTextDialog){
        EditTextDialog(
            modifier = Modifier,
            title = "Name of the new folder",
            defaultText = "folder name",
            onConfirm = {
                showEditTextDialog = false
                viewModel.newFolder(it)
            },
        ) { showEditTextDialog = false }
    }

    var showSortDialog by remember { mutableStateOf(false) }
    if(showSortDialog){
        SortDialog(
            items = viewModel.sortModeCallbackList(),
            selectedItem = sortModeSelected.value,
            onCancel = {
                showSortDialog = false
            }
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val storageButtonExpanded = remember{ mutableStateOf(false) }
        TextButton(
            onClick = {storageButtonExpanded.value=!storageButtonExpanded.value},
            modifier = Modifier
                .weight(1f)
        ){
            Text("Storage")
            MyDropdownMenu(
                contents = viewModel.toPair(storageList.value),
                expanded = storageButtonExpanded
            )
        }

        val moreButtonExpanded = remember{ mutableStateOf(false) }
        TextButton(
            modifier = Modifier
                .weight(1f)
                .wrapContentSize(),
            onClick = { moreButtonExpanded.value = !moreButtonExpanded.value}
        ) {
            Text("More")

            MyDropdownMenu(
                contents = listOf(
                    Pair("Create New Folder",{ showEditTextDialog = true}),
                    Pair("Sort",{showSortDialog = true}),
                    Pair("Exit",{viewModel.exit(activity)})
                ),
                expanded = moreButtonExpanded
            )
        }
    }
}