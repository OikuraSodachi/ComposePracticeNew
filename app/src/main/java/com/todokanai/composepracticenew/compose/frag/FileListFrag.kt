package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.BottomButtonListView
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.viewmodel.FileListViewModel

@Composable
fun FileListFrag(
    modifier: Modifier,
    selectMode: Int,
    selectedList: List<FileHolderItem>,
    onSelectModeChange: (Int) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit,
    onSwitchToRemote: () -> Unit = {},
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    viewModel: FileListViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = selectMode == AppConstants.MULTI_SELECT_MODE) {
        onSelectModeChange(AppConstants.DEFAULT_MODE)
        clearList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
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
                modifier = Modifier
                    .weight(1f),
                fileHolderItemList = uiState.value.fileHolderItemList,
                selectedList = selectedList,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it, selectMode) },
                onItemLongClick = { onSelectModeChange(AppConstants.MULTI_SELECT_MODE) },
                addToList = addToList,
                removeFromList = removeFromList,
                clearList = clearList
            )
        }

        BottomButtonListView(
            modifier = Modifier,
            selectMode = selectMode,
            onSelectModeChange = onSelectModeChange,
            onClearSelection = clearList,
            selectedList = selectedList,
            onConfirmDownload = onConfirmDownload,
            onEnterUploadMode = onEnterUploadMode
        )

        StorageSwitchBar(
            isRemote = false,
            onSwitchToLocal = {},
            onSwitchToRemote = onSwitchToRemote
        )
    }

}
