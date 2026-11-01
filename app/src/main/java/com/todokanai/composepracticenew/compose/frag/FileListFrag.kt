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
import com.todokanai.composepracticenew.ui.model.ListingKey
import com.todokanai.composepracticenew.viewmodel.ChildCountSource
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.FileSearchViewModel

/**
 * 로컬 파일 목록 화면. 경로·옵션 바, 파일 목록, 하단 작업 버튼, 저장소 전환 탭을 조합한다.
 * @param selectedList 선택 목록을 반환하는 함수 — 값을 실제로 쓰는 하위 컴포저블에서 읽어 선택 변경 시 리컴포지션 범위를 좁힌다
 * @param selectionSummary 정보 다이얼로그에 표시할 선택 요약을 반환하는 함수(null이면 다이얼로그를 표시하지 않음) — 다이얼로그 호스트에서만 읽는다
 * @param onShowInfo 선택 항목의 정보 다이얼로그를 여는 콜백
 * @param onDismissInfo 정보 다이얼로그를 닫는 콜백
 * @param onStartMultiSelect 드래그로 고른 첫 항목을 선택한 채로 다중 선택 모드로 전환하는 콜백
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백
 * @param childCountsFor 목록 조회 요청을 받아 그 결과의 폴더별 하위 항목 수를 읽는 창구를 돌려주는 함수. 상태를 바꾸지 않아 컴포지션 중에 부른다
 * @param searchViewModel 파일 검색의 열림 여부·검색어·결과를 맡는 ViewModel. 검색이 열려 있으면 목록 대신 검색 화면을 그린다
 */
@Composable
fun FileListFrag(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    selectedList: () -> List<FileHolderItem>,
    selectionSummary: () -> SelectionSummary?,
    onShowInfo: (List<FileHolderItem>) -> Unit,
    onDismissInfo: () -> Unit,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
    onResetSelection: () -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    childCountsFor: (ListingKey) -> ChildCountSource,
    onSwitchToRemote: () -> Unit = {},
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    navigateToStorage: () -> Unit,
    onExit: () -> Unit,
    viewModel: FileListViewModel,
    searchViewModel: FileSearchViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConflict by viewModel.pendingConflict.collectAsStateWithLifecycle()
    val pendingRename by viewModel.pendingRename.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    // dirTree 변경 시 NavController 블록 recompose 없이 FileListFrag 내부만 갱신
    val dirTree by viewModel.dirTree.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = selectMode == SelectMode.MultiSelect, onBack = onResetSelection)

    // 검색이 열려 있으면 목록 대신 검색 화면을 그린다 — destination이 아니라 이 화면 안의 상태라 백스택에 쌓이지 않는다
    searchState?.let { state ->
        FileSearchFrag(
            modifier = modifier,
            query = searchViewModel.query,
            state = state,
            onQueryChange = searchViewModel::onQueryChange,
            onClose = searchViewModel::close,
            onResultClick = { searchViewModel.onResultClick(it, selectMode) },
            onOpenLocation = searchViewModel::onOpenLocation
        )
        return
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
            suggestFolderName = viewModel::suggestNewFolderName,
            validateFolderName = viewModel::validateEntryName,
            onExit = onExit,
            // 다중 선택 중에는 검색을 열지 않는다 — 검색 결과의 선택·작업은 범위 밖이다
            onSearchClick = searchViewModel::open.takeIf { selectMode != SelectMode.MultiSelect }
        )
        DirectoryListingContent(
            modifier = Modifier.weight(1f),
            listing = uiState.value.listing,
            onRetry = viewModel::retryListing
        ) { loaded ->
            FileListView(
                modifier = Modifier.fillMaxSize(),
                fileHolderItemList = loaded.items,
                childCounts = childCountsFor(loaded.key),
                selectedList = selectedList,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it, selectMode) },
                onStartMultiSelect = onStartMultiSelect,
                onRangeSelect = onRangeSelect,
                addToList = addToList,
                removeFromList = removeFromList
            )
        }

        BottomButtonListView(
            modifier = Modifier,
            selectMode = selectMode,
            onSelectOperation = onSelectOperation,
            onResetSelection = onResetSelection,
            selectedList = selectedList,
            selectionSummary = selectionSummary,
            onShowInfo = onShowInfo,
            onDismissInfo = onDismissInfo,
            onConfirmDownload = onConfirmDownload,
            onEnterUploadMode = onEnterUploadMode,
            pendingConflict = pendingConflict,
            onRequestConfirm = viewModel::requestConfirm,
            onResolveConflict = viewModel::resolveConflict,
            onZip = viewModel::zip,
            suggestZipName = viewModel::suggestZipName,
            validateZipName = viewModel::validateZipName,
            canUnzip = viewModel::canUnzip,
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
