package com.todokanai.composepracticenew.compose.frag

import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.listview.RemoteBottomButtonListView
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

// TODO(#143-review-4): StorageFrag.StorageConnectionEffects와 구조 동일 — Flow를 직접 받는 TwoFlowEffects 공통 함수로 추출 고려해볼 것
/**
 * 원격 스토리지 연결 실패·유실 이벤트 Flow를 수집하고 Toast 및 콜백을 실행한다.
 *
 * @param viewModel 이벤트 Flow를 제공하는 ViewModel
 * @param onReconnectFailed 재연결 실패 시 실행할 콜백
 * @param onConnectionLost 연결 유실 시 실행할 콜백
 */
@Composable
private fun RemoteFileListConnectionEffects(
    viewModel: RemoteFileListViewModel,
    onReconnectFailed: () -> Unit,
    onConnectionLost: () -> Unit,
) {
    LaunchedEffect(Unit) {
        launch { viewModel.reconnectFailed.collect { onReconnectFailed() } }
        launch { viewModel.connectionLost.collect  { onConnectionLost()  } }
    }
}

/** 원격 스토리지의 파일 목록을 표시하고 I/O 작업(다운로드·업로드·이름변경·삭제·새폴더)을 제공하는 화면. */
@Composable
fun RemoteFileListFrag(
    modifier: Modifier,
    selectMode: SelectMode,
    selectedList: List<FileHolderItem>,
    selectedPaths: Set<String>,
    onSelectModeChange: (SelectMode) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    onSwitchToLocal: () -> Unit = {},
    onEnterDownloadMode: (List<FileHolderItem>) -> Unit = {},
    onConfirmUpload: () -> Unit = {},
    onConnectionLost: () -> Unit = {},
    navigateToStorage: () -> Unit,
    onExit: () -> Unit,
    dirTree: List<DirectoryItem>,
    onDirClick: (DirectoryItem) -> Unit,
    viewModel: RemoteFileListViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val sortModeCallbackList = viewModel.sortModeCallbackList

    val context = LocalContext.current
    RemoteFileListConnectionEffects(
        viewModel = viewModel,
        onReconnectFailed = {
            Toast.makeText(context, context.getString(R.string.toast_connection_failed), Toast.LENGTH_SHORT).show()
        },
        onConnectionLost = {
            Toast.makeText(context, context.getString(R.string.toast_connection_lost), Toast.LENGTH_SHORT).show()
            onConnectionLost()
        },
    )

    BackHandler(enabled = selectMode == SelectMode.MultiSelect) {
        onSelectModeChange(SelectMode.Default)
        clearList()
    }

    Column(modifier = modifier.fillMaxSize()) {
        OptionFrag(
            modifier = Modifier,
            navigateToStorage = navigateToStorage,
            dirTree = dirTree,
            onDirClick = onDirClick,
            sortMode = sortMode,
            sortModeCallbackList = sortModeCallbackList,
            onNewFolder = viewModel::onMakeDirectory,
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
                modifier = Modifier.weight(1f),
                fileHolderItemList = uiState.value.fileHolderItemList,
                selectedPaths = selectedPaths,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it) },
                onSelectModeChange = onSelectModeChange,
                onRangeSelect = onRangeSelect,
                addToList = addToList,
                removeFromList = removeFromList,
                clearList = clearList
            )
        }

        RemoteBottomButtonListView(
            modifier = Modifier,
            selectMode = selectMode,
            onSelectModeChange = onSelectModeChange,
            onClearSelection = clearList,
            selectedList = selectedList,
            onRename = viewModel::onRename,
            onDelete = viewModel::onDelete,
            onEnterDownloadMode = onEnterDownloadMode,
            onConfirmUpload = onConfirmUpload,
        )

        StorageSwitchBar(
            isRemote = true,
            onSwitchToLocal = onSwitchToLocal,
            onSwitchToRemote = {}
        )
    }
}
