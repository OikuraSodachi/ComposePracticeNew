package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.BottomButtonListView
import com.todokanai.composepracticenew.compose.listview.DirectoryListingContent
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.viewmodel.FileListViewModel

/**
 * 로컬 파일 목록 화면. 경로·옵션 바, 파일 목록, 하단 작업 버튼, 저장소 전환 탭을 조합한다.
 * @param selectedList 선택 목록을 반환하는 함수 — 값을 실제로 쓰는 하위 컴포저블에서 읽어 선택 변경 시 리컴포지션 범위를 좁힌다
 * @param selectionSummary 선택 목록 요약을 반환하는 함수 — 정보 다이얼로그가 열렸을 때만 읽는다
 */
@Composable
fun FileListFrag(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    selectedList: () -> List<FileHolderItem>,
    selectionSummary: () -> SelectionSummary,
    onSelectModeChange: (LocalSelectMode) -> Unit,
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
    val pendingRename by viewModel.pendingRename.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    // dirTree 변경 시 NavController 블록 recompose 없이 FileListFrag 내부만 갱신
    val dirTree by viewModel.dirTree.collectAsStateWithLifecycle()

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
            onSortModeSelected = viewModel::onSortModeSelected,
            onNewFolder = viewModel::newFolder,
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
                onItemClick = { viewModel.onItemClick(it, selectMode) },
                onStartMultiSelect = { onSelectModeChange(SelectMode.MultiSelect) },
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
            selectionSummary = selectionSummary,
            onConfirmDownload = onConfirmDownload,
            onEnterUploadMode = onEnterUploadMode,
            pendingConflict = pendingConflict,
            onRequestConfirm = viewModel::requestConfirm,
            onResolveConflict = viewModel::resolveConflict,
            onZip = viewModel::zip,
            validateZipName = viewModel::validateZipName,
            onRename = viewModel::rename,
            validateRename = viewModel::validateRename,
            pendingRename = pendingRename,
            onConfirmRename = viewModel::confirmRename,
            onCancelRename = viewModel::cancelRename,
            onDelete = viewModel::delete
        )

        StorageSwitchBar(
            isRemote = false,
            onSwitchToLocal = {},
            onSwitchToRemote = onSwitchToRemote
        )
    }

}
