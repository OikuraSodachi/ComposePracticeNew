package com.todokanai.composepracticenew.compose.frag

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.UnreadableServerDialog
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.DirectoryListingContent
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.listview.RemoteBottomButtonListView
import com.todokanai.composepracticenew.ui.model.RemoteSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/**
 * 원격 스토리지 재연결 실패 이벤트와 세션 끊김 상태를 관찰해 콜백을 실행한다.
 * 끊김 Toast는 끊긴 순간 앱 전역에서 띄우므로 여기서는 화면을 떠나는 콜백만 실행한다.
 *
 * @param viewModel 이벤트 Flow와 끊김 상태를 제공하는 ViewModel
 * @param onReconnectFailed 재연결 실패 시 실행할 콜백
 * @param onConnectionLost 세션이 끊긴 상태일 때(이 화면에 들어왔을 때 이미 끊겨 있던 경우 포함) 실행할 콜백
 */
@Composable
private fun RemoteFileListConnectionEffects(
    viewModel: RemoteFileListViewModel,
    onReconnectFailed: () -> Unit,
    onConnectionLost: () -> Unit,
) {
    val sessionLost by viewModel.sessionLost.collectAsStateWithLifecycle()

    // Unit 키 — 화면이 살아 있는 동안 한 번만 수집해, 리컴포지션마다 재연결 실패 이벤트를 다시 구독하지 않는다
    LaunchedEffect(Unit) {
        viewModel.reconnectFailed.collect { onReconnectFailed() }
    }
    // sessionLost를 키로 — 끊긴 상태가 되거나 끊긴 상태로 들어왔을 때 한 번 화면을 떠난다
    LaunchedEffect(sessionLost) {
        if (sessionLost) onConnectionLost()
    }
}

/**
 * 원격 스토리지의 파일 목록을 표시하고 I/O 작업(다운로드·업로드·이름변경·삭제·새폴더)을 제공하는 화면.
 * @param selectedList 선택 목록을 반환하는 함수 — 값을 실제로 쓰는 하위 컴포저블에서 읽어 선택 변경 시 리컴포지션 범위를 좁힌다
 * @param onStartMultiSelect 드래그로 고른 첫 항목을 선택한 채로 다중 선택 모드로 전환하는 콜백
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백
 */
@Composable
fun RemoteFileListFrag(
    modifier: Modifier,
    selectMode: RemoteSelectMode,
    selectedList: () -> List<FileHolderItem>,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onResetSelection: () -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
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
    val pendingRename by viewModel.pendingRename.collectAsStateWithLifecycle()
    val unreadableServer by viewModel.unreadableServer.collectAsStateWithLifecycle()

    val context = LocalContext.current
    RemoteFileListConnectionEffects(
        viewModel = viewModel,
        onReconnectFailed = {
            Toast.makeText(context, context.getString(R.string.toast_connection_failed), Toast.LENGTH_SHORT).show()
        },
        onConnectionLost = onConnectionLost,
    )

    if (unreadableServer) {
        UnreadableServerDialog(onDismiss = viewModel::dismissUnreadableServer)
    }

    BackHandler(enabled = selectMode == SelectMode.MultiSelect, onBack = onResetSelection)

    Column(modifier = modifier.fillMaxSize()) {
        OptionFrag(
            modifier = Modifier,
            navigateToStorage = navigateToStorage,
            dirTree = dirTree,
            onDirClick = onDirClick,
            sortMode = sortMode,
            onSortModeSelected = viewModel::onSortModeSelected,
            onNewFolder = viewModel::onMakeDirectory,
            suggestFolderName = viewModel::suggestNewFolderName,
            validateFolderName = viewModel::validateEntryName,
            onExit = onExit
        )
        DirectoryListingContent(
            modifier = Modifier.weight(1f),
            listing = uiState.value.listing,
            onRetry = viewModel::retryListing
        ) { items ->
            FileListView(
                modifier = Modifier.fillMaxSize(),
                fileHolderItemList = items,
                selectedList = selectedList,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it) },
                onStartMultiSelect = onStartMultiSelect,
                onRangeSelect = onRangeSelect,
                addToList = addToList,
                removeFromList = removeFromList
            )
        }

        RemoteBottomButtonListView(
            modifier = Modifier,
            selectMode = selectMode,
            onResetSelection = onResetSelection,
            selectedList = selectedList,
            onRename = viewModel::onRename,
            validateRename = viewModel::validateRename,
            pendingRename = pendingRename,
            onConfirmRename = viewModel::confirmRename,
            onCancelRename = viewModel::cancelRename,
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
