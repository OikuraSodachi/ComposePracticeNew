package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.StorageSwitchBar
import com.todokanai.composepracticenew.compose.listview.DirectoryListingContent
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.listview.RemoteBottomButtonListView
import com.todokanai.composepracticenew.compose.listview.RenameActions
import com.todokanai.composepracticenew.compose.listview.SelectAllBar
import com.todokanai.composepracticenew.compose.listview.rememberListingScrollState
import com.todokanai.composepracticenew.ui.model.RemoteSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.allowsPathBarNavigation
import com.todokanai.composepracticenew.ui.model.clickActionOf
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel
import com.todokanai.composepracticenew.viewmodel.SubmittedItem
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection
import kotlinx.coroutines.flow.StateFlow

/**
 * 연결된 원격 세션이 없는 상태를 관찰해 콜백을 실행한다.
 * 끊김은 화면 호스트가 앱 전역에서 알리므로 여기서는 화면을 떠나는 콜백만 실행한다.
 *
 * @param noSession 연결된 원격 세션이 없는지 나타내는 StateFlow. 이 helper 안에서 읽어 값이 바뀌어도 화면 전체를 다시 그리지 않는다
 * @param onNoSession 세션이 없는 상태일 때(끊겼거나, 세션 없이 이 화면이 만들어진 경우) 실행할 콜백
 */
@Composable
private fun RemoteFileListConnectionEffects(
    noSession: StateFlow<Boolean>,
    onNoSession: () -> Unit,
) {
    val isWithoutSession by noSession.collectAsStateWithLifecycle()

    // isWithoutSession을 키로 — 세션이 없어지거나 세션 없이 들어왔을 때 한 번 화면을 떠난다
    LaunchedEffect(isWithoutSession) {
        if (isWithoutSession) onNoSession()
    }
}

/**
 * 원격 스토리지의 파일 목록을 표시하고 I/O 작업(다운로드·업로드·이름변경·삭제·새폴더)을 제공하는 화면.
 * @param selectedList 선택 목록을 반환하는 함수 — 값을 실제로 쓰는 하위 컴포저블에서 읽어 선택 변경 시 리컴포지션 범위를 좁힌다
 * @param onStartMultiSelect 드래그로 고른 첫 항목을 선택한 채로 다중 선택 모드로 전환하는 콜백
 * @param onReplaceSelection 다중 선택 목록을 바꾸는 콜백 — 드래그 범위 선택과 모두 선택/해제에 쓴다
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백 — 취소·뒤로가기용
 * @param onSubmitSelection 확인한 작업의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param onSubmitItem 확인한 이름 변경의 대상으로 선택의 한 항목을 꺼내고 기본 모드로 되돌리는 콜백. 선택이 한 항목이 아니면 null
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
    onSubmitItem: () -> SubmittedItem?,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    onReplaceSelection: (List<FileHolderItem>) -> Unit,
    onSwitchToLocal: () -> Unit,
    onEnterDownloadMode: () -> Unit,
    onConfirmUpload: () -> Unit,
    onNoSession: () -> Unit,
    navigateToStorage: () -> Unit,
    onExit: () -> Unit,
    viewModel: RemoteFileListViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortModeStateHolder.sortMode.collectAsStateWithLifecycle()
    /** 경로 breadcrumb 목록. 이 값이 바뀌어도 NavController 블록은 다시 실행하지 않고 이 화면만 갱신한다. */
    val dirTree by viewModel.dirTreeStateHolder.dirTree.collectAsStateWithLifecycle()
    /** 원격 새 폴더를 만들 현재 폴더. 없으면 새 폴더 메뉴를 두지 않는다. */
    val newFolderTarget by viewModel.entryNaming.newFolderTarget.collectAsStateWithLifecycle()
    val pendingRename by viewModel.entryNaming.pendingRename.collectAsStateWithLifecycle()
    /** 목록 스크롤 상태. 목록(FileListView)을 화면에서 빼는 분기보다 위에서 만든다 — 위치가 남는 기간을 목록의 컴포지션 여부가 아니라 경로로 정한다 */
    val listState = rememberListingScrollState(uiState.value.listing)
    /** 이름 변경 다이얼로그의 콜백 묶음. */
    // viewModel·onSubmitItem을 키로 — 리컴포지션마다 새 묶음을 만들어 하단 버튼 영역의 건너뛰기가 깨지지 않도록 같은 ViewModel 동안 유지하고,
    // 꺼내는 콜백이 바뀌면 이전 콜백을 붙잡지 않도록 다시 만든다
    val renameActions = remember(viewModel, onSubmitItem) {
        RenameActions(
            onSubmitItem = onSubmitItem,
            onRename = viewModel::onRename,
            validate = viewModel.entryNaming::validateRename,
            onConfirmOverwrite = viewModel.entryNaming::confirmRename,
            onCancelOverwrite = viewModel.entryNaming::cancelRename,
        )
    }

    RemoteFileListConnectionEffects(
        noSession = viewModel.noSession,
        onNoSession = onNoSession,
    )

    BackHandler(enabled = selectMode == SelectMode.MultiSelect, onBack = onResetSelection)

    Column(modifier = modifier.fillMaxSize()) {
        OptionFrag(
            modifier = Modifier,
            navigateToStorage = navigateToStorage,
            dirTree = dirTree,
            // 다중 선택 중에는 눌러도 이동하지 않는다 — 다중 선택은 지금 폴더 안에서만 한다(#519)
            onDirClick = { entry -> if (selectMode.allowsPathBarNavigation) viewModel.dirTreeStateHolder.onDirClick(entry) },
            sortMode = sortMode,
            onSortModeSelected = viewModel.sortModeStateHolder::onSortModeSelected,
            newFolderTarget = newFolderTarget,
            onNewFolder = viewModel::onMakeDirectory,
            suggestFolderName = viewModel.entryNaming::suggestNewFolderName,
            validateFolderName = viewModel.entryNaming::validateNewFolderName,
            onExit = onExit
        )
        DirectoryListingContent(
            modifier = Modifier.weight(1f),
            listing = uiState.value.listing,
            onRetry = viewModel::retryListing
        ) { loaded ->
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectMode == SelectMode.MultiSelect) {
                    SelectAllBar(
                        modifier = Modifier,
                        items = loaded.items,
                        selectedList = selectedList,
                        onReplaceSelection = onReplaceSelection
                    )
                }
                FileListView(
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    fileHolderItemList = loaded.items,
                    // 원격 폴더는 하위 항목 수를 세지 않는다 — 세려면 폴더마다 LIST를 보내 하나뿐인 연결을 사용자 작업과 나눠 써야 한다(범위 밖)
                    childCounts = null,
                    selectedList = selectedList,
                    selectMode = selectMode,
                    clickActionOf = { selectMode.clickActionOf(it) },
                    onOpen = viewModel::openFolder,
                    onStartMultiSelect = onStartMultiSelect,
                    onReplaceSelection = onReplaceSelection,
                    addToList = addToList,
                    removeFromList = removeFromList
                )
            }
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
