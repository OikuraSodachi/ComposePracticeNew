package com.todokanai.composepracticenew.compose.frag

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.BottomButtonListView
import com.todokanai.composepracticenew.compose.listview.DirectoryListingContent
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.listview.LocalTransferActions
import com.todokanai.composepracticenew.compose.listview.RenameActions
import com.todokanai.composepracticenew.compose.listview.SelectionInfoActions
import com.todokanai.composepracticenew.compose.listview.CompressActions
import com.todokanai.composepracticenew.compose.listview.rememberListingScrollState
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.ListingKey
import com.todokanai.composepracticenew.viewmodel.ChildCountSource
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import com.todokanai.composepracticenew.viewmodel.FileSearchViewModel
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection
import kotlinx.coroutines.flow.Flow

/**
 * 로컬 파일 목록 화면. 경로·옵션 바, 파일 목록, 하단 작업 버튼, 저장소 전환 탭을 조합한다.
 * @param selectedList 선택 목록을 반환하는 함수 — 값을 실제로 쓰는 하위 컴포저블에서 읽어 선택 변경 시 리컴포지션 범위를 좁힌다
 * @param info 선택 항목 정보 다이얼로그의 표시 값과 콜백 묶음
 * @param transfer 다운로드 확인·업로드 모드 진입 콜백 묶음
 * @param onStartMultiSelect 드래그로 고른 첫 항목을 선택한 채로 다중 선택 모드로 전환하는 콜백
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백 — 취소·뒤로가기용
 * @param onSubmitSelection 확인한 작업의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param childCountsFor 목록 조회 요청을 받아 그 결과의 폴더별 하위 항목 수를 읽는 창구를 돌려주는 함수. 상태를 바꾸지 않아 컴포지션 중에 부른다
 * @param searchViewModel 파일 검색의 열림 여부·검색어·결과를 맡는 ViewModel. 검색이 열려 있으면 목록 대신 검색 화면을 그린다
 */
@Composable
fun FileListFrag(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    selectedList: () -> List<FileHolderItem>,
    info: SelectionInfoActions,
    transfer: LocalTransferActions,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
    onResetSelection: () -> Unit,
    onSubmitSelection: () -> SubmittedSelection?,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    childCountsFor: (ListingKey) -> ChildCountSource,
    onSwitchToRemote: () -> Unit,
    navigateToStorage: () -> Unit,
    onExit: () -> Unit,
    viewModel: FileListViewModel,
    searchViewModel: FileSearchViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConflict by viewModel.pendingConflict.collectAsStateWithLifecycle()
    val pendingRename by viewModel.pendingRename.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortModeStateHolder.sortMode.collectAsStateWithLifecycle()
    // dirTree 변경 시 NavController 블록 recompose 없이 FileListFrag 내부만 갱신
    val dirTree by viewModel.dirTreeStateHolder.dirTree.collectAsStateWithLifecycle()
    /** 새 폴더를 만들 현재 폴더. 없으면 새 폴더 메뉴를 두지 않는다. */
    val newFolderTarget by viewModel.newFolderTarget.collectAsStateWithLifecycle()
    /** 파일 검색의 결과 표시 상태. 여기서는 열림 여부만 읽고, 결과·검색어는 FileSearchFrag가 읽는다. */
    val searchState = searchViewModel.state.collectAsStateWithLifecycle()
    // searchState를 추적 — 결과 묶음·글자 입력마다 바뀌는 상태 대신 열림 여부가 바뀔 때만 이 화면을 다시 실행한다. searchState(State 객체)가 바뀌면 새로 만든다
    val isSearchOpen by remember(searchState) { derivedStateOf { searchState.value != null } }
    /** 목록 스크롤 상태. 검색이 열려 목록을 그리지 않는 동안에도 남도록 이른 return보다 위에서 만든다 — 검색을 닫으면 같은 경로의 스크롤 위치로 돌아온다 */
    val listState = rememberListingScrollState(uiState.value.listing)
    /** 압축 다이얼로그의 콜백 묶음. */
    // viewModel을 키로 — 리컴포지션마다 새 묶음을 만들어 하단 버튼 영역의 건너뛰기가 깨지지 않도록 같은 ViewModel 동안 유지한다
    val compressActions = remember(viewModel) {
        CompressActions(
            folderOf = viewModel::archiveFolderOf,
            onCompress = viewModel::compress,
            suggestName = viewModel::suggestArchiveName,
            validateName = viewModel::validateArchiveName,
        )
    }
    /** 이름 변경 다이얼로그의 콜백 묶음. */
    // viewModel을 키로 — 위와 같은 이유로 같은 ViewModel 동안 유지한다
    val renameActions = remember(viewModel) {
        RenameActions(
            onRename = viewModel::rename,
            validate = viewModel::validateRename,
            onConfirmOverwrite = viewModel::confirmRename,
            onCancelOverwrite = viewModel::cancelRename,
        )
    }

    BackHandler(enabled = selectMode == SelectMode.MultiSelect, onBack = onResetSelection)

    // 검색이 열려 있으면 목록 대신 검색 화면을 그린다 — destination이 아니라 이 화면 안의 상태라 백스택에 쌓이지 않는다
    if (isSearchOpen) {
        UnopenableFolderToast(unopenableFolder = searchViewModel.unopenableFolder)
        FileSearchFrag(modifier = modifier, selectMode = selectMode, viewModel = searchViewModel)
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
            onDirClick = viewModel.dirTreeStateHolder::onDirClick,
            sortMode = sortMode,
            onSortModeSelected = viewModel.sortModeStateHolder::onSortModeSelected,
            newFolderTarget = newFolderTarget,
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
                listState = listState,
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
            onSubmitSelection = onSubmitSelection,
            selectedList = selectedList,
            info = info,
            transfer = transfer,
            pendingConflict = pendingConflict,
            onRequestConfirm = viewModel::requestConfirm,
            onResolveConflict = viewModel::resolveConflict,
            compress = compressActions,
            canUnzip = viewModel::canUnzip,
            rename = renameActions,
            pendingRename = pendingRename,
            onDelete = viewModel::delete
        )

        StorageSwitchBar(
            isRemote = false,
            onSwitchToLocal = {},
            onSwitchToRemote = onSwitchToRemote
        )
    }

}

/**
 * 검색 결과의 폴더나 위치로 이동이 거부될 때마다 "폴더를 열 수 없습니다" Toast를 띄운다. 검색은 열린 채로 남는다.
 * @param unopenableFolder 이동이 거부됐음을 알리는 일회성 알림 Flow
 */
@Composable
private fun UnopenableFolderToast(unopenableFolder: Flow<Unit>) {
    /** Toast를 띄울 Context. */
    val context = LocalContext.current
    // unopenableFolder 키 — 같은 ViewModel의 알림이면 검색이 열린 동안 수집을 1회만 시작해 중복 수집을 방지
    LaunchedEffect(unopenableFolder) {
        unopenableFolder.collect { Toast.makeText(context, R.string.listing_failed_unreadable, Toast.LENGTH_SHORT).show() }
    }
}
