package com.todokanai.composepracticenew.compose.frag

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.UnreadableServerDialog
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.DirectoryListingContent
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.listview.RemoteBottomButtonListView
import com.todokanai.composepracticenew.compose.listview.RenameActions
import com.todokanai.composepracticenew.compose.listview.rememberListingScrollState
import com.todokanai.composepracticenew.ui.model.RemoteSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * 원격 스토리지 재연결 실패 이벤트와 세션 끊김 상태를 관찰해 콜백을 실행한다.
 * 끊김 Toast는 끊긴 순간 앱 전역에서 띄우므로 여기서는 화면을 떠나는 콜백만 실행한다.
 *
 * @param viewModel 이벤트 Flow와 끊김 상태를 제공하는 ViewModel
 * @param onReconnectFailed 재연결 실패 시 실행할 콜백
 * @param onReconnectSettingsUnreadable 설정을 읽지 못해 재연결할 서버를 알 수 없을 때 실행할 콜백
 * @param onConnectionLost 세션이 끊긴 상태일 때(이 화면에 들어왔을 때 이미 끊겨 있던 경우 포함) 실행할 콜백
 */
@Composable
private fun RemoteFileListConnectionEffects(
    viewModel: RemoteFileListViewModel,
    onReconnectFailed: () -> Unit,
    onReconnectSettingsUnreadable: () -> Unit,
    onConnectionLost: () -> Unit,
) {
    val sessionLost by viewModel.sessionLost.collectAsStateWithLifecycle()

    // Unit 키 — 화면이 살아 있는 동안 한 번만 수집해, 리컴포지션마다 재연결 실패 이벤트를 다시 구독하지 않는다
    LaunchedEffect(Unit) {
        viewModel.reconnectFailed.collect { onReconnectFailed() }
    }
    // Unit 키 — 위와 같은 이유로 한 번만 수집한다. 실패 이유가 달라 따로 받는다
    LaunchedEffect(Unit) {
        viewModel.reconnectSettingsUnreadable.collect { onReconnectSettingsUnreadable() }
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
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백 — 취소·뒤로가기용
 * @param onSubmitSelection 확인한 작업의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param onEnterDownloadMode 원격 선택을 꺼내 다운로드 모드로 진입하는 콜백
 */
@Composable
fun RemoteFileListFrag(
    modifier: Modifier,
    selectMode: RemoteSelectMode,
    selectedList: () -> List<FileHolderItem>,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onResetSelection: () -> Unit,
    onSubmitSelection: () -> SubmittedSelection?,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    onSwitchToLocal: () -> Unit,
    onEnterDownloadMode: () -> Unit,
    onConfirmUpload: () -> Unit,
    onConnectionLost: () -> Unit,
    navigateToStorage: () -> Unit,
    onExit: () -> Unit,
    viewModel: RemoteFileListViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortModeStateHolder.sortMode.collectAsStateWithLifecycle()
    /** 경로 breadcrumb 목록. 이 값이 바뀌어도 NavController 블록은 다시 실행하지 않고 이 화면만 갱신한다. */
    val dirTree by viewModel.dirTreeStateHolder.dirTree.collectAsStateWithLifecycle()
    /** 원격 새 폴더를 만들 현재 폴더. 없으면 새 폴더 메뉴를 두지 않는다. */
    val newFolderTarget by viewModel.newFolderTarget.collectAsStateWithLifecycle()
    val pendingRename by viewModel.pendingRename.collectAsStateWithLifecycle()
    val unreadableServer by viewModel.unreadableServer.collectAsStateWithLifecycle()
    /** 목록 스크롤 상태. 목록(FileListView)을 화면에서 빼는 분기보다 위에서 만든다 — 위치가 남는 기간을 목록의 컴포지션 여부가 아니라 경로로 정한다 */
    val listState = rememberListingScrollState(uiState.value.listing)
    /** 이름 변경 다이얼로그의 콜백 묶음. */
    // viewModel을 키로 — 리컴포지션마다 새 묶음을 만들어 하단 버튼 영역의 건너뛰기가 깨지지 않도록 같은 ViewModel 동안 유지한다
    val renameActions = remember(viewModel) {
        RenameActions(
            onRename = viewModel::onRename,
            validate = viewModel::validateRename,
            onConfirmOverwrite = viewModel::confirmRename,
            onCancelOverwrite = viewModel::cancelRename,
        )
    }

    val context = LocalContext.current
    RemoteFileListConnectionEffects(
        viewModel = viewModel,
        onReconnectFailed = {
            Toast.makeText(context, context.getString(R.string.toast_connection_failed), Toast.LENGTH_SHORT).show()
        },
        onReconnectSettingsUnreadable = {
            Toast.makeText(context, context.getString(R.string.toast_reconnect_settings_unreadable), Toast.LENGTH_SHORT).show()
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
            onDirClick = viewModel.dirTreeStateHolder::onDirClick,
            sortMode = sortMode,
            onSortModeSelected = viewModel.sortModeStateHolder::onSortModeSelected,
            newFolderTarget = newFolderTarget,
            onNewFolder = viewModel::onMakeDirectory,
            suggestFolderName = viewModel::suggestNewFolderName,
            validateFolderName = viewModel::validateEntryName,
            onExit = onExit
        )
        DirectoryListingContent(
            modifier = Modifier.weight(1f),
            listing = uiState.value.listing,
            onRetry = viewModel::retryListing
        ) { loaded ->
            FileListView(
                modifier = Modifier.fillMaxSize(),
                listState = listState,
                fileHolderItemList = loaded.items,
                // 원격 폴더는 하위 항목 수를 세지 않는다 — 세려면 폴더마다 LIST를 보내 하나뿐인 연결을 사용자 작업과 나눠 써야 한다(범위 밖)
                childCounts = null,
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
            onSubmitSelection = onSubmitSelection,
            selectedList = selectedList,
            rename = renameActions,
            pendingRename = pendingRename,
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
