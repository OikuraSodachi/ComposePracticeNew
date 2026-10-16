package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.BottomButtonListView
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.viewmodel.FileListViewModel

/** 로컬 파일 목록 화면. 경로·옵션 바, 파일 목록, 하단 작업 버튼, 저장소 전환 탭을 조합한다. */
@Composable
fun FileListFrag(
    modifier: Modifier,
    selectMode: SelectMode,
    selectedList: List<FileHolderItem>,
    selectedPaths: Set<String>,
    onSelectModeChange: (SelectMode) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    onSwitchToRemote: () -> Unit = {},
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    navigateToStorage: () -> Unit,
    onExit: () -> Unit,
    viewModel: FileListViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConflict by viewModel.pendingConflict.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    // dirTree 변경 시 NavController 블록 recompose 없이 FileListFrag 내부만 갱신
    val dirTree by viewModel.dirTree.collectAsStateWithLifecycle()
    val sortModeCallbackList = viewModel.sortModeCallbackList

    BackHandler(enabled = selectMode == SelectMode.MultiSelect) {
        onSelectModeChange(SelectMode.Default)
        clearList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        OptionFrag(
            modifier = Modifier,
            navigateToStorage = navigateToStorage,
            dirTree = dirTree,
            onDirClick = { viewModel.updateCurrentPath(it.path) },
            sortMode = sortMode,
            sortModeCallbackList = sortModeCallbackList,
            onNewFolder = viewModel::newFolder,
            onExit = onExit
        )
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
                selectedPaths = selectedPaths,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it, selectMode) },
                onSelectModeChange = onSelectModeChange,
                onRangeSelect = onRangeSelect,
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
            onEnterUploadMode = onEnterUploadMode,
            pendingConflict = pendingConflict,
            onRequestConfirm = viewModel::requestConfirm,
            onResolveConflict = viewModel::resolveConflict,
            onZip = viewModel::zip,
            onRename = viewModel::rename,
            onDelete = viewModel::delete
        )

        StorageSwitchBar(
            isRemote = false,
            onSwitchToLocal = {},
            onSwitchToRemote = onSwitchToRemote
        )
    }

}
